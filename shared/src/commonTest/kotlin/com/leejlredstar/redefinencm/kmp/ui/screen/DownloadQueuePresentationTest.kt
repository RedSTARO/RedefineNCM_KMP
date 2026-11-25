package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.SongDownloadTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadQueuePresentationTest {
    @Test
    fun activeTransfersAreShownBeforeWaitingAndFinishedRowsWithoutChangingCompletedOrder() {
        val presentation = DownloadQueuePresentation(
            listOf(
                task(17, DownloadTaskStatus.Completed),
                task(2, DownloadTaskStatus.Queued),
                task(3, DownloadTaskStatus.Downloading),
                task(4, DownloadTaskStatus.Completed),
                task(5, DownloadTaskStatus.Resolving),
                task(6, DownloadTaskStatus.SavingLyrics),
                task(7, DownloadTaskStatus.Paused),
                task(8, DownloadTaskStatus.Failed),
                task(9, DownloadTaskStatus.Cancelled),
                task(10, DownloadTaskStatus.Deleted),
            ),
        )

        assertEquals(DownloadFilter.entries, presentation.filters)
        assertEquals(10, presentation.count(DownloadFilter.All))
        assertEquals(4, presentation.count(DownloadFilter.Active))
        assertEquals(DownloadFilter.entries.drop(1), presentation.groups(DownloadFilter.All))
        assertEquals(listOf(3L, 5L, 6L, 2L), presentation.rows(DownloadFilter.Active).map { it.id })
        assertEquals(listOf(17L, 4L), presentation.rows(DownloadFilter.Completed).map { it.id })
        assertEquals(listOf(DownloadFilter.Completed), presentation.groups(DownloadFilter.Completed))
        assertEquals(10, presentation.groups(DownloadFilter.All).flatMap(presentation::rows).distinctBy { it.id }.size)
    }

    @Test
    fun emptySelectedStatusReturnsToAllAndDoesNotProduceAnEmptyGroup() {
        val presentation = DownloadQueuePresentation(listOf(task(1, DownloadTaskStatus.Completed)))
        assertEquals(listOf(DownloadFilter.All, DownloadFilter.Completed), presentation.filters)
        assertEquals(DownloadFilter.All, presentation.resolveFilter(DownloadFilter.Failed))
        assertEquals(listOf(DownloadFilter.Completed), presentation.groups(DownloadFilter.Failed))
        assertEquals(0, presentation.count(DownloadFilter.Failed))
        assertTrue(DownloadQueuePresentation(emptyList()).groups(DownloadFilter.All).isEmpty())
    }

    @Test
    fun savingAssetsIsActiveButNotPausableOrCancellable() {
        val presentation = DownloadQueuePresentation(listOf(task(1, DownloadTaskStatus.SavingLyrics)))
        assertEquals(1, presentation.count(DownloadFilter.Active))
        assertFalse(presentation.canPauseAll)
        assertFalse(presentation.canCancelAll)
        assertFalse(presentation.canResumeAll)
        assertFalse(presentation.hasFinished)
    }

    @Test
    fun bulkActionsReflectWhatTheQueueCanActuallyChange() {
        val pausedAndFinished = DownloadQueuePresentation(
            listOf(task(1, DownloadTaskStatus.Paused), task(2, DownloadTaskStatus.Deleted)),
        )
        assertFalse(pausedAndFinished.canPauseAll)
        assertTrue(pausedAndFinished.canCancelAll)
        assertTrue(pausedAndFinished.canResumeAll)
        assertTrue(pausedAndFinished.hasFinished)

        val waiting = DownloadQueuePresentation(listOf(task(1, DownloadTaskStatus.Queued)))
        assertTrue(waiting.canPauseAll)
        assertTrue(waiting.canCancelAll)
        assertFalse(waiting.canResumeAll)
        assertFalse(waiting.hasFinished)
    }

    private fun task(id: Long, status: DownloadTaskStatus) = SongDownloadTask(
        id = id,
        title = "Song $id",
        artist = "Artist",
        artworkUri = "",
        status = status,
    )
}
