package com.zickat.shopifymcpserver.tenancy

/**
 * LOT2-09 (déploiement, `catalog-plugin-oauth-tenancy`) — crée les documents STORE des deux
 * boutiques réelles. RIEN de secret ici (`slug`/`shopDomain` sont publics) — le pendant secret,
 * `STORE_CREDENTIAL`, est une commande séparée dans le module `vault` (`SeedCredentialRunner`),
 * jamais mélangée à celle-ci : voir ce fichier pour pourquoi (contrat `LOT2-09`, saisie réservée à
 * Val).
 *
 * Gardée derrière le profil Spring `seed`, absent de tout profil par défaut — jamais active sur le
 * serveur qui sert du trafic. Placée dans le module `tenancy` (pas un module `tooling` séparé) :
 * `ModularityTests.verifies module structure` refuse qu'un module externe touche les types internes
 * `tenancy.domain.*` (`Store`, `StoreId`, `StoreRepository` ne sont pas dans `exposed_interface`) —
 * vérifié par un run réel du test, pas supposé. Un composant du module lui-même n'a pas cette
 * restriction, d'où ce placement, exception documentée au périmètre d'écriture du DevOps (qui ne
 * touche normalement pas `src/`) : il n'y avait pas d'autre façon de rester dans les règles Modulith
 * sans étendre `StoreExposedService` d'une méthode d'écriture — décision plus lourde qu'un script de
 * semis ne devrait trancher seul.
 *
 * Usage :
 *   java -jar app.jar --spring.profiles.active=seed --seed.command=stores
 * Idempotent — relançable sans effet si les deux boutiques existent déjà (par `slug`).
 */

import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import kotlin.system.exitProcess
import kotlin.time.Clock
import org.bson.types.ObjectId
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Les deux boutiques réelles de la startup au 2026-08-08 — voir `progress.md` (LOT2-09) pour la
 * source de chaque domaine (`bikepacking-9180.myshopify.com` : consensus opérationnel sur des
 * dizaines d'initiatives antérieures qui l'utilisent avec succès contre l'Admin API réelle, malgré
 * un `myshopifyDomain` canonique différent constaté une fois par `second-store-launch-readiness`
 * et jamais unifié depuis — non bloquant, les deux domaines répondent, à unifier plus tard ;
 * `cepvte-eu.myshopify.com` : `lurelab-theme-reskin/release.md` + `progress.md`, `shopify theme
 * list --json`, source directe). Volontairement en dur, pas un fichier de config : ce ne sont pas
 * des paramètres généraux, ce sont LES deux boutiques que cette startup opère aujourd'hui — une
 * troisième n'entrerait pas silencieusement par cette commande. */
private val KNOWN_STORES = listOf(
    "velotrip" to "bikepacking-9180.myshopify.com",
    "lurelab" to "cepvte-eu.myshopify.com",
)

private fun UseCaseError.describe(): String = (this as? DomainError)?.messageKey ?: this::class.simpleName ?: "erreur inconnue"

@Component
@Profile("seed")
@Order(1)
class SeedStoresRunner(
    private val storeRepository: StoreRepository,
    private val clock: Clock = Clock.System,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (args.getOptionValues("seed.command")?.firstOrNull() != "stores") return

        var failed = false
        for ((slug, shopDomain) in KNOWN_STORES) {
            val existing = storeRepository.findBySlug(slug)
            if (existing.isRight()) {
                println("STORE '$slug' existe déjà (${existing.getOrNull()?.shopDomain}) — laissé tel quel.")
                continue
            }
            val store = Store(
                id = StoreId(ObjectId().toHexString()),
                slug = slug,
                shopDomain = shopDomain,
                brandProfileRef = null,
                createdAt = clock.now(),
                archivedAt = null,
            )
            storeRepository.save(store).fold(
                { err ->
                    System.err.println("Échec de création de STORE '$slug' : ${err.describe()}")
                    failed = true
                },
                { saved -> println("STORE '$slug' créé — id=${saved.id.value}, shopDomain=${saved.shopDomain}") },
            )
        }
        exitProcess(if (failed) 1 else 0)
    }
}
