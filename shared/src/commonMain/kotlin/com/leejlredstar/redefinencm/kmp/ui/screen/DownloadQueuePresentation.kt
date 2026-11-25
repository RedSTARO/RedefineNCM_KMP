package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.SongDownloadTask
import com.leejlredstar.redefinencm.kmp.download.canCancelDownloadTask
import com.leejlredstar.redefinencm.kmp.download.canPauseDownloadTask
import com.leejlredstar.redefinencm.kmp.i18n.strings

/** Each task belongs to exactly one visible status family in the unfiltered queue. */
internal enum class DownloadFilter {
    All,
    Active,
    Paused,
    Completed,
    Failed,
    Cancelled,
    Deleted,
    ;

    val label: String
        get() = when (this) {
            All -> strings.all
            Active -> strings.filterDownloading
            Paused -> strings.paused
            Completed -> strings.downloaded
            Failed -> strings.failed
            Cancelled -> strings.canceled
            Deleted -> strings.fileDeleted
        }
}

internal class DownloadQueuePresentation(tasks: List<SongDownloadTask>) {
    private val total = tasks.size
    private val byStatus = tasks.groupBy { task ->
        when (task.status) {
            DownloadTaskStatus.Queued,
            DownloadTaskStatus.Resolving,
            DownloadTaskStatus.Downloading,
            DownloadTaskStatus.SavingLyrics -> DownloadFilter.Active
            DownloadTaskStatus.Paused -> DownloadFilter.Paused
            DownloadTaskStatus.Completed -> DownloadFilter.Completed
            DownloadTaskStatus.Failed -> DownloadFilter.Failed
            DownloadTaskStatus.Cancelled -> DownloadFilter.Cancelled
            DownloadTaskStatus.Deleted -> DownloadFilter.Deleted
        }
    }
    // Show the row doing work before rows waiting to start, without changing worker or play order.
    private val activeByPhase = byStatus[DownloadFilter.Active].orEmpty().let { active ->
        val (inFlight, waiting) = active.partition { it.status != DownloadTaskStatus.Queued }
        inFlight + waiting
    }

    val filters = DownloadFilter.entries.filter { it == DownloadFilter.All || count(it) > 0 }
    val canPauseAll = activeByPhase.any(::canPauseDownloadTask)
    val canResumeAll = count(DownloadFilter.Paused) > 0
    val canCancelAll = activeByPhase.any(::canCancelDownloadTask) || canResumeAll
    val hasFinished = tasks.any { it.isTerminal }

    fun count(filter: DownloadFilter): Int = if (filter == DownloadFilter.All) total else byStatus[filter].orEmpty().size

    fun resolveFilter(filter: DownloadFilter): DownloadFilter = filter.takeIf { it in filters } ?: DownloadFilter.All

    fun groups(filter: DownloadFilter): List<DownloadFilter> = when (val selected = resolveFilter(filter)) {
        DownloadFilter.All -> filters.drop(1)
        else -> listOf(selected)
    }

    fun rows(group: DownloadFilter): List<SongDownloadTask> {
        require(group != DownloadFilter.All)
        return if (group == DownloadFilter.Active) activeByPhase else byStatus[group].orEmpty()
    }
}
