package com.antixray.deobfuscation;

import com.antixray.nms.NmsAdapter;
import com.antixray.util.Frustum;
import com.antixray.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisibilityResolverTest {

    private static final String WORLD_NAME = "world";

    private VisibilityResolver resolverWithNoFilters;
    private VisibilityResolver resolverWithFrustum;
    private VisibilityResolver resolverWithRaycast;
    private MaterialSet materialSet;
    private NmsAdapter nmsAdapter;
    private MockedStatic<Bukkit> bukkitMock;
    private World world;
    private Player player;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        lenient().when(server.getName()).thenReturn("TestServer");
        lenient().when(server.getVersion()).thenReturn("1.21");
        lenient().when(server.getBukkitVersion()).thenReturn("1.21-R0.1-SNAPSHOT");
        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        world = mock(World.class);
        lenient().when(world.getName()).thenReturn(WORLD_NAME);
        lenient().when(world.getMinHeight()).thenReturn(-64);
        lenient().when(world.getMaxHeight()).thenReturn(320);

        nmsAdapter = mock(NmsAdapter.class);
        lenient().when(nmsAdapter.getBlockStateAt(any(World.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(0);

        materialSet = Mockito.spy(new MaterialSet(1, 2, 3, 4));

        player = mock(Player.class);
        Location eyeLocation = new Location(world, 0.0, 64.0, 0.0, 0f, 0f);
        lenient().when(player.getEyeLocation()).thenReturn(eyeLocation);
        lenient().when(player.getWorld()).thenReturn(world);

        resolverWithNoFilters = new VisibilityResolver(nmsAdapter, materialSet, false, false, 0, 0);
        resolverWithFrustum = new VisibilityResolver(nmsAdapter, materialSet, true, false, 0, 0);
        resolverWithRaycast = new VisibilityResolver(nmsAdapter, materialSet, false, true, 100, 0);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Test
    void noFiltersAlwaysVisible() {
        assertTrue(resolverWithNoFilters.isVisible(10, 64, 10));
        assertTrue(resolverWithNoFilters.isVisible(-100, -64, -100));
    }

    @Test
    void noFiltersIsVisibleWithCoords() {
        assertTrue(resolverWithNoFilters.isVisible(0, 0, 0));
    }

    @Test
    void frustumCullingEnabledFlag() {
        assertTrue(resolverWithFrustum.isFrustumCullingEnabled());
        assertFalse(resolverWithNoFilters.isFrustumCullingEnabled());
    }

    @Test
    void raycastEnabledFlag() {
        assertTrue(resolverWithRaycast.isRaycastEnabled());
        assertFalse(resolverWithNoFilters.isRaycastEnabled());
    }

    @Test
    void frustumNotUpdatedBeforeUpdateFrustumCall() {
        Frustum frustum = resolverWithFrustum.getFrustum();
        assertFalse(frustum.isEnabled());
        assertTrue(resolverWithFrustum.isVisible(0, 0, 0));
    }

    @Test
    void frustumUpdateEnablesFrustum() {
        resolverWithFrustum.updateFrustum(player, 70.0);
        Frustum frustum = resolverWithFrustum.getFrustum();
        assertTrue(frustum.isEnabled());
    }

    @Test
    void frustumBlockInFrontIsVisible() {
        resolverWithFrustum.updateFrustum(player, 70.0);
        assertTrue(resolverWithFrustum.isVisible(0, 64, 10));
    }

    @Test
    void frustumBlockBehindIsNotVisible() {
        resolverWithFrustum.updateFrustum(player, 70.0);
        assertFalse(resolverWithFrustum.isVisible(0, 64, -10));
    }

    @Test
    void frustumIsVisibleWithCoords() {
        resolverWithFrustum.updateFrustum(player, 70.0);
        assertTrue(resolverWithFrustum.isVisible(0, 64, 10));
        assertFalse(resolverWithFrustum.isVisible(0, 64, -10));
    }

    @Test
    void noFilterFrustumUpdateDoesNotEnableFrustum() {
        resolverWithNoFilters.updateFrustum(player, 70.0);
        Frustum frustum = resolverWithNoFilters.getFrustum();
        assertFalse(frustum.isEnabled());
    }

    @Test
    void getFrustumReturnsNonNull() {
        assertNotNull(resolverWithNoFilters.getFrustum());
        assertNotNull(resolverWithFrustum.getFrustum());
        assertNotNull(resolverWithRaycast.getFrustum());
    }

    @Test
    void raycastLineOfSightWithClearPath() {
        lenient().when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);
        assertTrue(resolverWithRaycast.isVisible(player, 0, 64, 5));
    }

    @Test
    void raycastLineOfSightWithBlockedPath() {
        lenient().when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(2)))
                .thenReturn(5);
        lenient().when(nmsAdapter.getBlockStateAt(eq(world), eq(0), eq(64), eq(2)))
                .thenReturn(5);
        doReturn(false).when(materialSet).isTransparent(5);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int x = invocation.getArgument(1, Integer.class);
                    int y = invocation.getArgument(2, Integer.class);
                    int z = invocation.getArgument(3, Integer.class);
                    if (x == 0 && y == 64 && z == 2) return 5;
                    return -1;
                });

        assertFalse(resolverWithRaycast.isVisible(player, 0, 64, 5));
    }

    @Test
    void bothFrustumAndRaycastEnabled() {
        VisibilityResolver both = new VisibilityResolver(nmsAdapter, materialSet, true, true, 100, 0);
        both.updateFrustum(player, 70.0);

        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenReturn(-1);
        assertTrue(both.isVisible(player, 0, 64, 5));

        assertFalse(both.isVisible(player, 0, 64, -5));
    }

    @Test
    void frustumCullingRejectsBeforeRaycast() {
        VisibilityResolver both = new VisibilityResolver(nmsAdapter, materialSet, true, true, 100, 0);
        both.updateFrustum(player, 70.0);
        assertFalse(both.isVisible(player, 0, 64, -10));
    }

    @Test
    void frustumInFrontIncludedBehindExcluded() {
        resolverWithFrustum.updateFrustum(player, 70.0);
        assertTrue(resolverWithFrustum.isVisible(0, 64, 10),
                "Block in front of player should be visible");
        assertFalse(resolverWithFrustum.isVisible(0, 64, -10),
                "Block strictly behind player should not be visible");
    }

    @Test
    void raycastSolidWallBlocksVisibility() {
        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(1, Integer.class);
                    int y = inv.getArgument(2, Integer.class);
                    int z = inv.getArgument(3, Integer.class);
                    if (x == 0 && y == 64 && z == 2) return 5;
                    return -1;
                });
        doReturn(false).when(materialSet).isTransparent(5);

        assertFalse(resolverWithRaycast.isVisible(player, 0, 64, 5),
                "Solid wall at z=2 should block line of sight to z=5");
    }

    @Test
    void raycastClearLineOfSightPasses() {
        when(nmsAdapter.getBlockStateAt(eq(world), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int x = inv.getArgument(1, Integer.class);
                    int y = inv.getArgument(2, Integer.class);
                    int z = inv.getArgument(3, Integer.class);
                    int id = 100 + x + y + z;
                    return id;
                });
        doReturn(true).when(materialSet).isTransparent(anyInt());

        assertTrue(resolverWithRaycast.isVisible(player, 0, 64, 5),
                "Clear path through transparent blocks should allow line of sight");
    }
}
