package com.zickat.shopifymcpserver.audit

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

/**
 * `LOT0-07.md`, « comment on vérifie que c'est fini » : « aucun chemin du code (recherché
 * structurellement, pas seulement par convention) n'appelle `update`/`delete`/`replace` sur la
 * collection `AUDIT_LOG`. »
 *
 * Complète — ne remplace pas — `AuditLogAppendOnlyTest` (`LOT0-03`), qui vérifie par réflexion
 * qu'aucune des trois classes connues du chemin `AuditLogRepository` (contrat, implémentation
 * Mongo, fake) ne DÉCLARE de méthode `update`/`delete`/`remove`/`upsert`. C'est une garantie « par
 * convention » : elle suppose que seules ces trois classes touchent la collection. Ce test-ci lève
 * cette hypothèse : il parcourt **tout** `src/main/kotlin` — pas une liste de classes attendues —
 * et vérifie qu'aucun fichier référençant le journal d'audit n'appelle un verbe mutant de l'API
 * MongoDB. Un futur développeur qui ajouterait, dans une classe totalement différente (un
 * contrôleur d'administration, une migration…), un appel `mongoTemplate.updateFirst(...,
 * AuditLogEntity.COLLECTION_NAME)` ferait échouer CE test, pas seulement le premier.
 *
 * **Pourquoi une recherche de code source plutôt qu'un test Testcontainers classique** : l'
 * invariant vérifié ici est un invariant de CHEMIN DE CODE (« quel code appelle quoi »), pas un
 * invariant observable par une base en cours d'exécution — aucune restriction n'est posée au
 * niveau du moteur MongoDB à ce lot (voir KDoc `AuditLogAppendOnlyTest`, LOT0-03 : « aucune
 * restriction de rôle base de données n'est mise en place à ce lot »), donc une instance Mongo
 * réelle n'observerait rien de plus qu'un test statique ne voit déjà. Ce choix est documenté ici
 * explicitement (voir aussi `progress.md`, entrée LOT0-07) plutôt que silencieusement substitué.
 */
class AuditLogNoMutationAcrossCodebaseTest {

    private val mainSourceRoot = File("src/main/kotlin")
    private val forbiddenCallPattern =
        Regex("""\.(update\w*|delete\w*|remove\w*|replace\w*|upsert\w*|findAndModify|findAndDelete|findAndReplace)\s*\(""")
    private val auditCollectionMarkers = listOf("AuditLogEntity", "AuditLogRepository", "AuditLog(", "auditLogs")

    /**
     * Retire les commentaires de bloc (`/** … */`, `/* … */`) et de ligne (`// …`) avant la
     * recherche — sans ça, la KDoc qui DOCUMENTE l'absence de `.remove()`/`.updateFirst()` (voir
     * `AuditLogMongoRepository`) se fait passer pour un appel réel. Ce test cherche des appels de
     * code, pas des occurrences de texte.
     */
    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `no source file in the whole main tree that references the audit log collection calls a mutating Mongo operation`() {
        check(mainSourceRoot.isDirectory) { "expected ${mainSourceRoot.absolutePath} to exist — check the working directory" }

        val offending = mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to stripComments(it.readText()) }
            .filter { (_, code) -> auditCollectionMarkers.any { marker -> code.contains(marker) } }
            .filter { (_, code) -> forbiddenCallPattern.containsMatchIn(code) }
            .map { (file, _) -> file.path }
            .toList()

        offending shouldBe emptyList()
    }
}
