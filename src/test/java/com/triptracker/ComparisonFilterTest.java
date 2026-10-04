package com.triptracker;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the trip-selection checklist filter in {@link TripComparisonPanel}.
 *
 * The filter is a VIEW over which checkboxes are shown; it must never mutate
 * {@code selectedTripIds}. These tests assert on panel state (which checkboxes
 * are present, their labels and selected flag, and the empty-state label) rather
 * than Swing rendering, per the testing standards.
 */
public class ComparisonFilterTest {

    private EnhancedLootTrackerPlugin mockPlugin;
    private List<Trip> trips;
    private TripComparisonPanel panel;

    @Before
    public void setUp() throws Exception {
        mockPlugin = mock(EnhancedLootTrackerPlugin.class);

        AtomicInteger idSeq = new AtomicInteger(1);
        when(mockPlugin.getNextTripNumber()).thenAnswer(inv -> idSeq.getAndIncrement());
        when(mockPlugin.getExcludedItems()).thenReturn(new HashSet<>());
        when(mockPlugin.getExcludedNpcs()).thenReturn(new HashSet<>());

        Trip guards1 = new Trip("Guards 1", mockPlugin);
        Trip guards2 = new Trip("Guards 2", mockPlugin);
        Trip vorkath = new Trip("Vorkath", mockPlugin);
        Trip zulrah = new Trip("Zulrah", mockPlugin);
        trips = new ArrayList<>(Arrays.asList(guards1, guards2, vorkath, zulrah));

        final int preSelected = guards1.getTripId();
        onEdt(() -> panel = new TripComparisonPanel(trips, preSelected, () -> {}, mockPlugin));
    }

    @Test
    public void noFilterShowsAllTrips() throws Exception {
        List<String> labels = checkboxLabels();
        assertEquals(trips.size(), labels.size());
        assertTrue(labels.containsAll(Arrays.asList("Guards 1", "Guards 2", "Vorkath", "Zulrah")));
    }

    @Test
    public void substringMatchIsCaseInsensitive() throws Exception {
        applyFilter("guards");

        List<String> labels = checkboxLabels();
        assertEquals(2, labels.size());
        assertTrue(labels.containsAll(Arrays.asList("Guards 1", "Guards 2")));
    }

    @Test
    public void noMatchShowsEmptyStateLabel() throws Exception {
        applyFilter("zzz");

        assertEquals(0, checkboxLabels().size());
        assertTrue("Expected a 'No trips match' label when nothing matches", hasNoMatchLabel());
    }

    @Test
    public void clearingFilterRestoresFullList() throws Exception {
        applyFilter("guards");
        assertEquals(2, checkboxLabels().size());

        applyFilter("");
        assertEquals(trips.size(), checkboxLabels().size());
        assertFalse(hasNoMatchLabel());
    }

    @Test
    public void selectionPersistsWhenTripIsFilteredOutThenBackIn() throws Exception {
        // Pre-select Zulrah via the selection set (the single source of truth).
        int zulrahId = trips.get(3).getTripId();
        addSelectedId(zulrahId);

        // Filter Zulrah out of view.
        applyFilter("guards");
        assertFalse("Zulrah checkbox should be hidden by the filter", checkboxLabels().contains("Zulrah"));

        // The selection must survive even though the checkbox is not shown.
        assertTrue("Filtered-out trip must stay selected", selectedTripIds().contains(zulrahId));

        // Clear the filter: Zulrah comes back, still checked.
        applyFilter("");
        JCheckBox zulrahBox = checkboxByLabel("Zulrah");
        assertNotNull(zulrahBox);
        assertTrue("Re-shown checkbox must reflect selectedTripIds", zulrahBox.isSelected());
    }

    @Test
    public void checkboxReflectsSelectionWhenReShown() throws Exception {
        int vorkathId = trips.get(2).getTripId();
        addSelectedId(vorkathId);

        applyFilter("zulrah");
        assertNull(checkboxByLabel("Vorkath"));
        assertTrue(selectedTripIds().contains(vorkathId));

        applyFilter("vork");
        JCheckBox vorkBox = checkboxByLabel("Vorkath");
        assertNotNull(vorkBox);
        assertTrue(vorkBox.isSelected());
    }

    // === Filter-aware All / None ===

    @Test
    public void selectAllVisibleWithFilterActive() throws Exception {
        int guards1 = trips.get(0).getTripId();
        int guards2 = trips.get(1).getTripId();
        int zulrahId = trips.get(3).getTripId();

        // A selected trip that the filter will hide.
        addSelectedId(zulrahId);

        applyFilter("guards");
        selectVisible();

        Set<Integer> ids = selectedTripIds();
        assertTrue("Visible 'Guards 1' should be selected", ids.contains(guards1));
        assertTrue("Visible 'Guards 2' should be selected", ids.contains(guards2));
        assertTrue("Filtered-out 'Zulrah' must stay selected", ids.contains(zulrahId));
    }

    @Test
    public void deselectAllVisibleWithFilterActive() throws Exception {
        int guards1 = trips.get(0).getTripId();
        int guards2 = trips.get(1).getTripId();
        int vorkathId = trips.get(2).getTripId();
        int zulrahId = trips.get(3).getTripId();

        // Select every trip, then filter to a subset and deselect-visible.
        addSelectedId(guards1);
        addSelectedId(guards2);
        addSelectedId(vorkathId);
        addSelectedId(zulrahId);

        applyFilter("guards");
        deselectVisible();

        Set<Integer> ids = selectedTripIds();
        assertFalse("Visible 'Guards 1' should be deselected", ids.contains(guards1));
        assertFalse("Visible 'Guards 2' should be deselected", ids.contains(guards2));
        assertTrue("Filtered-out 'Vorkath' must stay selected", ids.contains(vorkathId));
        assertTrue("Filtered-out 'Zulrah' must stay selected", ids.contains(zulrahId));
    }

    @Test
    public void noFilterSelectAllThenNoneHasNoRegression() throws Exception {
        // No filter: All selects every trip, None clears every selection.
        applyFilter("");
        selectVisible();

        Set<Integer> all = new HashSet<>();
        for (Trip t : trips) {
            all.add(t.getTripId());
        }
        assertEquals("All (no filter) should select every trip", all, selectedTripIds());

        deselectVisible();
        assertTrue("None (no filter) should clear every selection", selectedTripIds().isEmpty());
    }

    @Test
    public void workedExampleSelectionPersistsAcrossFilterAndButtons() throws Exception {
        int guards1 = trips.get(0).getTripId();
        int guards2 = trips.get(1).getTripId();
        int vorkathId = trips.get(2).getTripId();
        int zulrahId = trips.get(3).getTripId();

        // Pre-select a trip that will be filtered out (the "Zulrah" in the example).
        addSelectedId(zulrahId);

        // Filter to the matching subset and select everything visible.
        applyFilter("guards");
        selectVisible();

        Set<Integer> afterSelect = selectedTripIds();
        assertTrue(afterSelect.contains(guards1));
        assertTrue(afterSelect.contains(guards2));
        assertTrue("Pre-selected filtered-out trip stays selected", afterSelect.contains(zulrahId));
        assertFalse("Non-matching 'Vorkath' not selected", afterSelect.contains(vorkathId));

        // Deselect everything visible: only the Guards go, Zulrah stays.
        deselectVisible();
        Set<Integer> afterDeselect = selectedTripIds();
        assertFalse(afterDeselect.contains(guards1));
        assertFalse(afterDeselect.contains(guards2));
        assertTrue("Filtered-out 'Zulrah' still selected", afterDeselect.contains(zulrahId));

        // Clear the filter: Guards render unchecked, Zulrah checked, Vorkath unchecked.
        applyFilter("");
        assertFalse(checkboxByLabel("Guards 1").isSelected());
        assertFalse(checkboxByLabel("Guards 2").isSelected());
        assertTrue(checkboxByLabel("Zulrah").isSelected());
        assertFalse(checkboxByLabel("Vorkath").isSelected());
    }

    // === Helpers ===

    /**
     * Mirrors the "All" button handler: on the EDT, for every trip matching the
     * active filter (via the panel's private {@code matchesFilter}), add its id.
     */
    private void selectVisible() throws Exception {
        onEdt(() -> {
            try {
                Set<Integer> ids = selectedTripIds();
                for (Trip trip : trips) {
                    if (matchesFilter(trip)) {
                        ids.add(trip.getTripId());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Mirrors the "None" button handler: on the EDT, for every trip matching the
     * active filter, remove its id (never clear()).
     */
    private void deselectVisible() throws Exception {
        onEdt(() -> {
            try {
                Set<Integer> ids = selectedTripIds();
                for (Trip trip : trips) {
                    if (matchesFilter(trip)) {
                        ids.remove(trip.getTripId());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean matchesFilter(Trip trip) throws Exception {
        Method m = findMethod(panel.getClass(), "matchesFilter", Trip.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(panel, trip);
    }

    /**
     * Sets filterText directly and invokes rebuildChecklist() on the EDT, bypassing
     * the debounce timer so the view updates synchronously and deterministically.
     */
    private void applyFilter(String text) throws Exception {
        onEdt(() -> {
            try {
                setField(panel, "filterText", text.trim().toLowerCase());
                invokePrivate(panel, "rebuildChecklist");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void addSelectedId(int id) throws Exception {
        @SuppressWarnings("unchecked")
        Set<Integer> ids = (Set<Integer>) getField(panel, "selectedTripIds");
        ids.add(id);
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> selectedTripIds() throws Exception {
        return (Set<Integer>) getField(panel, "selectedTripIds");
    }

    private JPanel checklistPanel() throws Exception {
        return (JPanel) getField(panel, "checklistPanel");
    }

    private List<String> checkboxLabels() throws Exception {
        List<String> labels = new ArrayList<>();
        for (Component c : checklistPanel().getComponents()) {
            if (c instanceof JCheckBox) {
                labels.add(((JCheckBox) c).getText());
            }
        }
        return labels;
    }

    private JCheckBox checkboxByLabel(String label) throws Exception {
        for (Component c : checklistPanel().getComponents()) {
            if (c instanceof JCheckBox && label.equals(((JCheckBox) c).getText())) {
                return (JCheckBox) c;
            }
        }
        return null;
    }

    private boolean hasNoMatchLabel() throws Exception {
        for (Component c : checklistPanel().getComponents()) {
            if (c instanceof JLabel && "No trips match".equals(((JLabel) c).getText())) {
                return true;
            }
        }
        return false;
    }

    private void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private void invokePrivate(Object target, String methodName) throws Exception {
        Method m = findMethod(target.getClass(), methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
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

    private Method findMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
