package com.triptracker;

import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@code onConfigChanged} routes each config key to exactly ONE refresh:
 * exclusion keys ({@code excludedItems}/{@code excludedNpcs}) go through the in-place-aware
 * {@code refreshAfterExclusionChange()} and never also trigger {@code rebuildAfterLoad()}
 * (proving the previous double-rebuild is gone), while {@code spriteDisplayMode} still forces a
 * full {@code rebuildAfterLoad()}. Unrelated keys trigger nothing.
 */
public class ExclusionRefreshRoutingTest {

    private EnhancedLootTrackerPlugin plugin;
    private EnhancedLootTrackerPanel mockPanel;

    @Before
    public void setUp() throws Exception {
        plugin = new EnhancedLootTrackerPlugin();
        mockPanel = mock(EnhancedLootTrackerPanel.class);
        setField(plugin, "panel", mockPanel);
    }

    @Test
    public void excludedNpcsRoutesToSingleInPlaceAwareRefresh() throws Exception {
        plugin.onConfigChanged(configChanged("triptracker", "excludedNpcs"));
        drainEdt();

        verify(mockPanel, times(1)).refreshAfterExclusionChange();
        verify(mockPanel, never()).rebuildAfterLoad();
    }

    @Test
    public void excludedItemsRoutesToSingleInPlaceAwareRefresh() throws Exception {
        plugin.onConfigChanged(configChanged("triptracker", "excludedItems"));
        drainEdt();

        verify(mockPanel, times(1)).refreshAfterExclusionChange();
        verify(mockPanel, never()).rebuildAfterLoad();
    }

    @Test
    public void spriteDisplayModeStillForcesFullRebuild() throws Exception {
        plugin.onConfigChanged(configChanged("triptracker", "spriteDisplayMode"));
        drainEdt();

        verify(mockPanel, times(1)).rebuildAfterLoad();
        verify(mockPanel, never()).refreshAfterExclusionChange();
    }

    @Test
    public void unrelatedKeyTriggersNoRefresh() throws Exception {
        plugin.onConfigChanged(configChanged("triptracker", "someOtherKey"));
        drainEdt();

        verify(mockPanel, never()).refreshAfterExclusionChange();
        verify(mockPanel, never()).rebuildAfterLoad();
    }

    @Test
    public void otherConfigGroupIsIgnored() throws Exception {
        plugin.onConfigChanged(configChanged("someothergroup", "excludedNpcs"));
        drainEdt();

        verify(mockPanel, never()).refreshAfterExclusionChange();
        verify(mockPanel, never()).rebuildAfterLoad();
    }

    private ConfigChanged configChanged(String group, String key) {
        ConfigChanged event = mock(ConfigChanged.class);
        when(event.getGroup()).thenReturn(group);
        when(event.getKey()).thenReturn(key);
        return event;
    }

    /** The handler schedules refreshes via SwingUtilities.invokeLater; flush the EDT before verifying. */
    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
