package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.ChineseShoeSizeSystem
import com.zickat.shopifymcpserver.products.domain.models.ShoeSizeRow
import com.zickat.shopifymcpserver.products.domain.models.SizeConversionResult
import kotlin.math.roundToLong

object SizeConversion {

    val SHOE_SIZE_TABLE_CN_TO_EU: List<ShoeSizeRow> = listOf(
        ShoeSizeRow(footLengthCm = 22.5, euSize = 35.5, cnOldSize = 35),
        ShoeSizeRow(footLengthCm = 23.0, euSize = 36.0, cnOldSize = 36),
        ShoeSizeRow(footLengthCm = 23.5, euSize = 37.0, cnOldSize = 37),
        ShoeSizeRow(footLengthCm = 24.0, euSize = 38.0, cnOldSize = 38),
        ShoeSizeRow(footLengthCm = 24.5, euSize = 39.0, cnOldSize = 39),
        ShoeSizeRow(footLengthCm = 25.0, euSize = 40.0, cnOldSize = 40),
        ShoeSizeRow(footLengthCm = 25.5, euSize = 40.5, cnOldSize = 41),
        ShoeSizeRow(footLengthCm = 26.0, euSize = 41.5, cnOldSize = 42),
        ShoeSizeRow(footLengthCm = 26.5, euSize = 42.0, cnOldSize = 43),
        ShoeSizeRow(footLengthCm = 27.0, euSize = 43.0, cnOldSize = 44),
        ShoeSizeRow(footLengthCm = 27.5, euSize = 44.0, cnOldSize = 45),
        ShoeSizeRow(footLengthCm = 28.0, euSize = 44.5, cnOldSize = 46),
    )

    val GARMENT_LETTER_SIZE_CATEGORIES: List<String> = listOf("Vêtement cycliste", "Cuissard vélo")

    fun convertShoeFootLengthCmToEu(footLengthCm: Double): SizeConversionResult {
        val table = SHOE_SIZE_TABLE_CN_TO_EU
        val min = table.first()
        val max = table.last()

        if (footLengthCm < min.footLengthCm || footLengthCm > max.footLengthCm) {
            return SizeConversionResult.Blocked(
                "Longueur de pied ${footLengthCm}cm hors de la plage sourcée " +
                    "(${min.footLengthCm}–${max.footLengthCm}cm, voir SHOE_SIZE_TABLE_CN_TO_EU) — " +
                    "pas d'extrapolation, à signaler plutôt qu'à deviner.",
            )
        }

        val exact = table.firstOrNull { it.footLengthCm == footLengthCm }
        if (exact != null) {
            return SizeConversionResult.Covered(
                euSize = exact.euSize,
                sourceNote = "Correspondance exacte, table sourcée (voir en-tête de fichier).",
            )
        }

        val lower = table.last { it.footLengthCm < footLengthCm }
        val upper = table.first { it.footLengthCm > footLengthCm }
        val ratio = (footLengthCm - lower.footLengthCm) / (upper.footLengthCm - lower.footLengthCm)
        val interpolated = lower.euSize + ratio * (upper.euSize - lower.euSize)

        return SizeConversionResult.Covered(
            euSize = (interpolated * 2).roundToLong() / 2.0,
            sourceNote = "Interpolation linéaire entre ${lower.footLengthCm}cm (EU ${lower.euSize}) et " +
                "${upper.footLengthCm}cm (EU ${upper.euSize}), table sourcée.",
        )
    }

    fun convertChineseShoeNumberToEu(value: Double, system: ChineseShoeSizeSystem): SizeConversionResult {
        if (system == ChineseShoeSizeSystem.MONDOPOINT_MM) {
            return convertShoeFootLengthCmToEu(value / 10)
        }

        val row = SHOE_SIZE_TABLE_CN_TO_EU.firstOrNull { it.cnOldSize.toDouble() == value }
            ?: return SizeConversionResult.Blocked(
                "Taille chinoise (système ancien) \"$value\" hors de la plage sourcée — voir SHOE_SIZE_TABLE_CN_TO_EU.",
            )
        return SizeConversionResult.Covered(
            euSize = row.euSize,
            sourceNote = "Correspondance exacte, table sourcée (voir en-tête de fichier), système ancien (旧码).",
        )
    }

    fun convertGarmentLetterSize(rawLetterSize: String): SizeConversionResult.Blocked =
        SizeConversionResult.Blocked(
            "Taille \"$rawLetterSize\" : aucune table de correspondance lettre→lettre fiable entre " +
                "tailles fournisseur (Chine/Asie) et tailles françaises n'existe (recherche BRAND-05, " +
                "sources citées dans mcp-shopify-catalog/src/size-conversion-data.ts) — l'écart réel " +
                "dépend de la marque/du vendeur, pas d'une règle fixe. Utiliser les mesures corporelles " +
                "réelles (cm) de la fiche produit si disponibles, sinon signaler/bloquer plutôt que deviner.",
        )
}
