package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.protocol.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the production controller at its real Forge listener seams. */
class RejectedLoginRegressionTest {
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final Connection connection = mock(Connection.class);
    private final List<Runnable> queue = new ArrayList<>();
    private final GameProfile offline = new GameProfile(UUID.nameUUIDFromBytes(
            "OfflinePlayer:RegressionPlayer".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RegressionPlayer");
    private final GameProfile premium = new GameProfile(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"), "RegressionPlayer");
    private GameProfile profile = offline;
    private ServerLoginController controller;
    private ServerLoginController.Access access;
    private MigrationTransaction transaction;
    private int disconnects;
    private MockedStatic<TrueuuidConfig> config;
    private MockedStatic<Trueuuid> logging;
    private MockedStatic<LoginFailureService> failures;
    private MockedStatic<SessionCheck> sessions;
    private MockedStatic<VerifiedProfileService> profiles;
    private final CompletableFuture<Optional<VerifiedProfile>> verification = new CompletableFuture<>();
    private final CompletableFuture<Void> migration = new CompletableFuture<>();

    @BeforeEach void setup() {
        config = mockStatic(TrueuuidConfig.class);
        config.when(TrueuuidConfig::timeoutMs).thenReturn(30000L);
        logging = mockStatic(Trueuuid.class);
        failures = mockStatic(LoginFailureService.class);
        sessions = mockStatic(SessionCheck.class);
        profiles = mockStatic(VerifiedProfileService.class);
        when(connection.isConnected()).thenReturn(true); // asynchronous close: transport still appears open
        when(server.isSameThread()).thenReturn(true);
        doAnswer(i -> { queue.add(i.getArgument(0)); return null; }).when(server).execute(any(Runnable.class));
        TrueuuidRuntime.AUTH_STATE = mock(AuthState.class);
        TrueuuidRuntime.MIGRATION_LOCKS = new MigrationLockRegistry();
        TrueuuidRuntime.MIGRATIONS = mock(MigrationCoordinator.class);
        transaction = mock(MigrationTransaction.class);
        when(TrueuuidRuntime.MIGRATIONS.forServer(server)).thenReturn(transaction);
        when(transaction.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(transaction.migrate(anyString(), any())).thenReturn(migration);
        sessions.when(() -> SessionCheck.hasJoinedAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(verification);
        sessions.when(() -> SessionCheck.publicClientIpOrEmpty(any())).thenReturn("");
        profiles.when(() -> VerifiedProfileService.create(any())).thenReturn(premium);
        access = new ServerLoginController.Access() {
            public GameProfile profile() { return profile; }
            public void profile(GameProfile p) { profile = p; }
            public MinecraftServer server() { return server; }
            public Connection connection() { return connection; }
            public void disconnect(Component reason) { disconnects++; controller.onDisconnect(); }
        };
        controller = new ServerLoginController(access);
        failures.when(() -> LoginFailureService.handle(any(), any(), any(), anyString(), anyBoolean(), any()))
                .thenAnswer(i -> {
                    ((Consumer<Component>) i.getArgument(5)).accept(mock(Component.class));
                    return i.getArgument(0);
                });
        controller.afterProfileInitialized();
        assertTrue(ready().isCancelled());
    }

    @AfterEach void cleanup() {
        profiles.close(); sessions.close(); failures.close(); logging.close(); config.close();
        TrueuuidRuntime.MIGRATION_LOCKS.close();
    }

    @Test void deniedOfflineAnswerCannotReachPlayerCreationOrSaveDuringAsyncClose() throws Exception {
        answer(false, false);
        drain();
        assertEquals(1, disconnects);
        assertNeverAccepted();
        controller.afterProfileInitialized(); // even a delayed hello must not revive this connection
        assertNeverAccepted();
    }

    @Test void externalDisconnectBeforeAnswerIsTerminal() throws Exception {
        controller.onDisconnect();
        answer(true, false);
        drain();
        assertNeverAccepted();
        sessions.verifyNoInteractions();
    }

    @Test void cleanupWithoutAnAuthenticationDecisionCannotGrantAcceptance() throws Exception {
        var reset = ServerLoginController.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(controller);
        assertTrue(ready().isCancelled(), "generic cleanup must not grant admission");
        assertSame(offline, profile);
    }

    @Test void transportClosureWithoutListenerCallbackIsTerminal() {
        when(connection.isConnected()).thenReturn(false);
        assertTrue(tick().isCancelled());
        when(connection.isConnected()).thenReturn(true);
        assertNeverAccepted();
    }

    @Test void delayedSuccessfulVerifierCannotReviveDisconnectedSession() throws Exception {
        answer(true, false);
        drain();
        verification.complete(Optional.of(new VerifiedProfile(premium.getId(), premium.getName(), List.of())));
        controller.onDisconnect();
        drain();
        assertSame(offline, profile);
        profiles.verify(() -> VerifiedProfileService.record(any(), any(), any(), any(), any()), never());
        assertNeverAccepted();
    }

    @Test void successfulPremiumVerificationStillAccepts() throws Exception {
        answer(true, false); drain();
        verification.complete(Optional.of(new VerifiedProfile(premium.getId(), premium.getName(), List.of())));
        drain();
        assertSame(premium, profile);
        assertFalse(ready().isCancelled());
        assertEquals(0, disconnects);
    }

    @Test void explicitlyPermittedOfflineFallbackStillAccepts() throws Exception {
        failures.when(() -> LoginFailureService.handle(any(), any(), any(), anyString(), anyBoolean(), any()))
                .thenReturn(offline);
        answer(false, false); drain();
        assertFalse(ready().isCancelled());
        assertSame(offline, profile);
        assertEquals(0, disconnects);
    }

    @Test void migrationDeclineAndTimeoutNeverAccept() throws Exception {
        offerMigration();
        answer(false, false); drain();
        assertNeverAccepted();
    }

    @Test void migrationTimeoutCannotFallThroughSameTick() throws Exception {
        offerMigration();
        field("trueuuid$sentAt").setLong(controller, 1L);
        assertTrue(tick().isCancelled());
        assertNeverAccepted();
    }

    @Test void authenticationTimeoutNeverAccepts() throws Exception {
        field("trueuuid$sentAt").setLong(controller, 1L);
        assertTrue(tick().isCancelled());
        assertNeverAccepted();
    }

    @Test void malformedAnswerDenialRemainsTerminal() throws Exception {
        var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[] {127}));
        try { controller.onLoginCustom(new ServerboundCustomQueryPacket(
                field("trueuuid$txId").getInt(controller), data), callback()); }
        finally { data.release(); }
        drain();
        assertEquals(1, disconnects);
        assertNeverAccepted();
    }

    @Test void failedMigrationNeverFallsBackToOfflineAcceptance() throws Exception {
        offerMigration();
        answer(true, true); drain();
        migration.completeExceptionally(new IllegalStateException("synthetic IO failure")); drain();
        assertFalse(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
        assertSame(offline, profile);
        assertNeverAccepted();
    }

    @Test void delayedVerifierFailureCannotReopenClosedSession() throws Exception {
        answer(true, false); drain();
        controller.onDisconnect();
        verification.completeExceptionally(new IllegalStateException("synthetic verifier failure")); drain();
        failures.verifyNoInteractions();
        assertNeverAccepted();
    }

    @Test void disconnectedMigrationKeepsRetryBlockedUntilWorkerActuallyFinishes() throws Exception {
        offerMigration();
        answer(true, true); drain();
        assertTrue(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
        controller.onDisconnect();
        assertTrue(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
        assertFalse(migration.isCancelled(), "canceling a future does not stop its file-writing worker");
        migration.complete(null); drain();
        assertFalse(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
        assertSame(offline, profile);
        assertNeverAccepted();
    }

    @Test void successfulMigrationFinalizesPremiumIdentity() throws Exception {
        offerMigration();
        answer(true, true); drain();
        migration.complete(null); drain();
        assertSame(premium, profile);
        assertFalse(ready().isCancelled());
        assertFalse(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
    }

    @Test void newConnectionCanRetryOnlyAfterDisconnectedMigrationWorkerFinishes() throws Exception {
        offerMigration();
        answer(true, true); drain();
        controller.onDisconnect();
        var blockedRetry = new ServerLoginController(access);
        blockedRetry.afterProfileInitialized();
        var blocked = callback(); blockedRetry.onReadyToAccept(blocked);
        assertTrue(blocked.isCancelled());
        assertTrue(TrueuuidRuntime.MIGRATION_LOCKS.contains(premium.getName()));
        migration.complete(null); drain();
        when(transaction.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        controller = new ServerLoginController(access);
        controller.afterProfileInitialized();
        assertTrue(ready().isCancelled());
        answer(true, false); drain();
        assertSame(premium, profile);
        assertFalse(ready().isCancelled());
    }

    @Test void nettyAnswerIsProcessedOnServerThreadAndCannotOvertakeDisconnect() throws Exception {
        when(server.isSameThread()).thenReturn(false);
        answer(false, false);
        assertEquals(0, disconnects, "Netty must not mutate the acceptance gate");
        controller.onDisconnect();
        drain();
        assertNeverAccepted();
    }

    private void offerMigration() throws Exception {
        when(transaction.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.of(
                new MigrationTransaction.Offer(offline.getId(), "playerdata"))));
        answer(true, false); drain();
        verification.complete(Optional.of(new VerifiedProfile(premium.getId(), premium.getName(), List.of())));
        drain();
    }

    private void answer(boolean joined, boolean confirmed) throws Exception {
        int tx = field("trueuuid$txId").getInt(controller);
        var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(AuthWireCodec.encodeAnswer(
                new AuthMessages.Answer(joined, "", confirmed, false))));
        try { controller.onLoginCustom(new ServerboundCustomQueryPacket(tx, data), callback()); }
        finally { data.release(); }
    }
    private Field field(String name) throws Exception {
        Field f = ServerLoginController.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private CallbackInfo ready() { var ci = callback(); controller.onReadyToAccept(ci); return ci; }
    private CallbackInfo tick() { var ci = callback(); controller.onTick(ci); return ci; }
    private static CallbackInfo callback() { return new CallbackInfo("test", true); }
    private void drain() { while (!queue.isEmpty()) queue.remove(0).run(); }
    private void assertNeverAccepted() {
        int savedOfflineFiles = 0;
        boolean allTicksCancelled = true;
        for (int i = 0; i < 3; i++) {
            // Listener continuation is the boundary before PlayerList creates/saves a player.
            if (!ready().isCancelled()) savedOfflineFiles++;
            allTicksCancelled &= tick().isCancelled();
        }
        int continuations = savedOfflineFiles;
        boolean cancelled = allTicksCancelled;
        assertAll(() -> assertEquals(0, continuations, "denied session reached player creation/save continuation"),
                () -> assertTrue(cancelled, "terminal sessions must cancel every tick"));
    }
}
