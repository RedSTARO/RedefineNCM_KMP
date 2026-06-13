package com.leejlredstar.redefinencm.kmp.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Media3 DataSource.Factory that intercepts `redefinencm://playbackPlaceHolder?id=xxx` URIs
 * and resolves them to real CDN stream URLs via [StreamUrlResolver] before delegating to the
 * wrapped factory. All other URIs are passed through unchanged.
 */
@OptIn(UnstableApi::class)
class RedirectingDataSourceFactory(
    private val defaultFactory: DataSource.Factory,
    private val resolver: StreamUrlResolver,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        RedirectingDataSource(defaultFactory.createDataSource(), resolver)
}

@OptIn(UnstableApi::class)
class RedirectingDataSource(
    private val wrapped: DataSource,
    private val resolver: StreamUrlResolver,
) : DataSource {

    private var resolvedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        wrapped.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = if (dataSpec.uri.scheme == "redefinencm" && dataSpec.uri.host == "playbackPlaceHolder") {
            val mediaId = dataSpec.uri.getQueryParameter("id")
                ?: throw IOException("Missing id parameter in placeholder URI: ${dataSpec.uri}")
            val resolved = runBlocking { resolver.resolve(mediaId) }
                ?: throw IOException("Failed to resolve stream URL for mediaId=$mediaId")
            Uri.parse(resolved).also { resolvedUri = it }
        } else {
            dataSpec.uri.also { resolvedUri = it }
        }
        return wrapped.open(dataSpec.buildUpon().setUri(uri).build())
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
        wrapped.read(buffer, offset, readLength)

    override fun getUri(): Uri? = resolvedUri

    override fun close() = wrapped.close()
}
