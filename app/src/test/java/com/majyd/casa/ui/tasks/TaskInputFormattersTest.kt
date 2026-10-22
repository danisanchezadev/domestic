package com.majyd.casa.ui.tasks

import com.majyd.casa.data.model.TaskAssignmentType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskInputFormattersTest {
    @Test
    fun date_usesDayMonthYearAndRoundTrips() {
        val date = LocalDate.of(2026, 12, 25)

        assertEquals("25-12-2026", TaskInputFormatters.formatDate(date))
        assertEquals(date, TaskInputFormatters.parseDate("25-12-2026"))
    }

    @Test
    fun date_rejectsIsoAndInvalidValues() {
        assertNull(TaskInputFormatters.parseDate("2026-12-25"))
        assertNull(TaskInputFormatters.parseDate("31-02-2026"))
    }

    @Test
    fun personAssignment_replacesPreviousSelection() {
        assertEquals(
            setOf("person-2"),
            toggleTaskPerson(TaskAssignmentType.PERSON, setOf("person-1"), "person-2"),
        )
        assertEquals(
            setOf("person-1", "person-2"),
            toggleTaskPerson(TaskAssignmentType.MULTIPLE_PEOPLE, setOf("person-1"), "person-2"),
        )
    }
}
