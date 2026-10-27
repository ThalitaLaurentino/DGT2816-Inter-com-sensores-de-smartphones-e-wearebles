package com.doma.assist

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Complication "Doma" no mostrador do relógio: um atalho que abre o
 * app direto da watch face. Com o TalkBack, a descrição é falada e um
 * toque duplo abre o Doma Assist — sem navegar na lista de apps.
 */
class DomaComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type)

    override suspend fun onComplicationRequest(
        request: ComplicationRequest
    ): ComplicationData? = buildData(request.complicationType)

    private fun buildData(type: ComplicationType): ComplicationData? {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val image = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        ).build()
        val description = PlainComplicationText
            .Builder(getString(R.string.complication_description)).build()

        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, description)
                    .setTapAction(tapIntent)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText
                        .Builder(getString(R.string.complication_text)).build(),
                    description
                )
                    .setTapAction(tapIntent)
                    .setMonochromaticImage(image)
                    .build()
            else -> null
        }
    }
}
