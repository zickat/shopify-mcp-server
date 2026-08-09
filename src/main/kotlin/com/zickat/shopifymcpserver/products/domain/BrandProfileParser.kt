package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.zickat.shopifymcpserver.products.domain.models.BrandProfile
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileContent
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileDesign
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileLocalization
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileTone
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

object BrandProfileParser {

    private val yamlMapper = ObjectMapper(YAMLFactory())

    fun parse(raw: String, brandProfileRef: String): Either<UseCaseError, BrandProfile> {
        val parsed = runCatching { yamlMapper.readValue(raw, Any::class.java) }
            .getOrElse { return invalid("brandProfile.invalid.yaml", brandProfileRef) }
        val root = parsed as? Map<*, *> ?: return invalid("brandProfile.invalid.notAnObject", brandProfileRef)
        return validateShape(root, brandProfileRef)
    }

    private fun validateShape(root: Map<*, *>, ref: String): Either<UseCaseError, BrandProfile> {
        val storeId = root["store_id"] as? String
        if (storeId.isNullOrBlank()) return invalid("brandProfile.invalid.storeId", ref)

        val name = root["name"] as? String
        if (name.isNullOrBlank()) return invalid("brandProfile.invalid.name", ref)

        val toneRaw = root["tone"] as? Map<*, *> ?: return invalid("brandProfile.invalid.tone.missing", ref)
        val voice = toneRaw["voice"] as? String
        if (voice.isNullOrBlank()) return invalid("brandProfile.invalid.tone.voice", ref)
        val forbiddenTopics = toneRaw["forbidden_topics"].asStringList()
            ?: return invalid("brandProfile.invalid.tone.forbiddenTopics", ref)
        val blockedOriginTermsRaw = toneRaw["blocked_origin_terms"]
        val blockedOriginTerms = if (blockedOriginTermsRaw == null) {
            emptyList()
        } else {
            blockedOriginTermsRaw.asStringList() ?: return invalid("brandProfile.invalid.tone.blockedOriginTerms", ref)
        }

        val localizationRaw = root["localization"] as? Map<*, *> ?: return invalid("brandProfile.invalid.localization.missing", ref)
        val convertSupplierSizesToFr = localizationRaw["convert_supplier_sizes_to_fr"] as? Boolean
            ?: return invalid("brandProfile.invalid.localization.convertSupplierSizesToFr", ref)
        val applicableCategories = localizationRaw["applicable_categories"].asStringList()
            ?: return invalid("brandProfile.invalid.localization.applicableCategories", ref)

        val designRaw = root["design"] as? Map<*, *> ?: return invalid("brandProfile.invalid.design.missing", ref)
        val colorsRaw = designRaw["colors"] as? Map<*, *> ?: return invalid("brandProfile.invalid.design.colors", ref)
        if (!colorsRaw.containsKey("primary")) return invalid("brandProfile.invalid.design.colors.primary", ref)
        val primaryColor = colorsRaw["primary"]
        if (primaryColor != null && primaryColor !is String) return invalid("brandProfile.invalid.design.colors.primary", ref)
        val secondaryColor = colorsRaw["secondary"]
        if (secondaryColor != null && secondaryColor !is String) return invalid("brandProfile.invalid.design.colors.secondary", ref)

        val contentRaw = root["content"] as? Map<*, *> ?: return invalid("brandProfile.invalid.content.missing", ref)
        val articleAuthorName = contentRaw["article_author_name"] as? String
        if (articleAuthorName.isNullOrBlank()) return invalid("brandProfile.invalid.content.articleAuthorName", ref)

        return BrandProfile(
            storeId = storeId,
            name = name,
            tone = BrandProfileTone(voice = voice, forbiddenTopics = forbiddenTopics, blockedOriginTerms = blockedOriginTerms),
            localization = BrandProfileLocalization(convertSupplierSizesToFr, applicableCategories),
            design = BrandProfileDesign(primaryColor = primaryColor as String?, secondaryColor = secondaryColor as String?),
            content = BrandProfileContent(articleAuthorName),
        ).right()
    }

    private fun Any?.asStringList(): List<String>? {
        val list = this as? List<*> ?: return null
        return if (list.all { it is String }) list.map { it as String } else null
    }

    private fun invalid(messageKey: String, brandProfileRef: String): Either<UseCaseError, BrandProfile> =
        DomainError(messageKey, mapOf("brandProfileRef" to brandProfileRef)).left()
}
