package com.zickat.shopifymcpserver.tenancy

/**
 * D39/D40 (`catalog-plugin-oauth-tenancy`) — accorde un `GRANT` : la seule porte qui transforme une
 * identité résolue (JWT valide, auto-créée par `IdentityUseCase.findOrCreate`) en droit d'appeler les
 * outils d'une boutique. À l'usage exclusif de Val : jamais un agent, jamais un script, jamais un
 * fichier committé. Suppose que la boutique visée existe déjà (`SeedStoresRunner`,
 * `--seed.command=stores`, à lancer d'abord si besoin) — le pendant secret, `STORE_CREDENTIAL`, est une
 * commande séparée (`SeedCredentialRunner`, module `vault`), jamais mélangée à celle-ci.
 *
 * Gardée derrière le profil Spring `seed`, absent de tout profil par défaut — jamais active sur le
 * serveur qui sert du trafic. Placée dans le module `tenancy` (pas un module `tooling` séparé) :
 * `ModularityTests.verifies module structure` refuse qu'un module externe touche les types internes
 * `tenancy.domain.*` (`Grant`, `GrantRole`, `GrantRepository` ne sont pas dans `exposed_interface`) —
 * même raisonnement de placement que `SeedStoresRunner`, vérifié par un run réel du test.
 *
 * Sur le patron exact de `SeedCredentialRunner` : `System.console()` obligatoire, refus explicite d'un
 * flux non interactif (aucun `System.console()` disponible sous pipe/redirection) — et, au-delà du
 * seul patron secret, refus de tout argument de ligne de commande ou variable d'environnement pour le
 * contenu de l'octroi lui-même (identité créditée, rôle, durée). Qui reçoit quel accès et pour combien
 * de temps est aussi sensible qu'un secret vis-à-vis d'un historique de shell ou d'un journal — seul
 * `--seed.command=grant`, le sélecteur de commande partagé par tous les runners `seed`, reste un
 * argument.
 *
 * Usage (terminal interactif obligatoire — jamais de pipe, jamais un argument, jamais une variable
 * d'environnement pour le contenu de l'octroi) :
 *   java -jar app.jar --spring.profiles.active=seed --seed.command=grant
 */

import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import org.bson.types.ObjectId
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

private fun UseCaseError.describe(): String = (this as? DomainError)?.messageKey ?: this::class.simpleName ?: "erreur inconnue"

@Component
@Profile("seed")
@Order(3)
class SeedGrantRunner(
    private val grantRepository: GrantRepository,
    private val storeRepository: StoreRepository,
    private val identityExposedService: IdentityExposedService,
    private val clock: Clock = Clock.System,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (args.getOptionValues("seed.command")?.firstOrNull() != "grant") return

        val console = System.console()
        if (console == null) {
            System.err.println(
                "Aucun terminal interactif attaché (System.console() est null) — cette commande refuse " +
                    "de lire un octroi depuis un flux non interactif (pipe/redirection), où rien ne " +
                    "garantit que le contenu ne finisse pas dans un historique ou un journal. Lancer ce " +
                    "jar directement dans un terminal.",
            )
            exitProcess(1)
        }

        println("Octroi d'un GRANT — rien de ce qui suit n'est jamais journalisé ni affiché ailleurs que sur ce terminal.")

        val slug = console.readLine("  slug de la boutique (velotrip|lurelab) : ")
        if (slug.isNullOrBlank()) {
            System.err.println("Le slug de la boutique est obligatoire — abandon, rien n'a été écrit en base.")
            exitProcess(1)
        }
        val store = storeRepository.findBySlug(slug).fold({ null }, { it })
        if (store == null) {
            System.err.println("Boutique '$slug' introuvable — lancer --seed.command=stores d'abord si besoin.")
            exitProcess(1)
        }

        val roleInput = console.readLine("  rôle à accorder (viewer|operator) : ")
        val role = roleInput?.let { GrantRole.fromWireValue(it).fold({ null }, { r -> r }) }
        if (role == null) {
            System.err.println("Rôle '$roleInput' invalide — attendu : viewer ou operator.")
            exitProcess(1)
        }

        val issuer = console.readLine("  issuer JWT de l'identité créditée : ")
        val subject = console.readLine("  subject JWT (sub) de l'identité créditée : ")
        if (issuer.isNullOrBlank() || subject.isNullOrBlank()) {
            System.err.println("issuer et subject sont obligatoires — abandon, rien n'a été écrit en base.")
            exitProcess(1)
        }
        val identityId = identityExposedService.resolve(issuer, subject).fold(
            { err ->
                System.err.println("Échec de résolution de l'identité créditée : ${err.describe()}")
                exitProcess(1)
            },
            { it },
        )

        val grantedBy = console.readLine("  identityId de l'accordant (Val, déjà connu — jamais auto-créé ici) : ")
        if (grantedBy.isNullOrBlank()) {
            System.err.println("grantedBy est obligatoire — abandon, rien n'a été écrit en base.")
            exitProcess(1)
        }

        val expiresAt = if (role == GrantRole.OPERATOR) {
            val minutesInput = console.readLine("  durée de validité en minutes (obligatoire pour operator) : ")
            val durationMinutes = minutesInput?.toLongOrNull()
            if (durationMinutes == null || durationMinutes <= 0) {
                System.err.println("Durée invalide — un entier positif de minutes est obligatoire pour un grant operator.")
                exitProcess(1)
            }
            clock.now() + durationMinutes.minutes
        } else {
            null
        }

        val grant = Grant(
            id = GrantId(ObjectId().toHexString()),
            identityId = identityId,
            storeId = store.id,
            role = role,
            grantedBy = grantedBy,
            createdAt = clock.now(),
            expiresAt = expiresAt,
            revokedAt = null,
        )

        val exitCode = grantRepository.save(grant).fold(
            { err ->
                System.err.println("Échec de création du GRANT : ${err.describe()}")
                1
            },
            { saved ->
                val expiryNote = saved.expiresAt?.let { "expire à $it" } ?: "sans expiration"
                println("GRANT créé — id=${saved.id.value}, boutique='$slug', rôle=${saved.role.wireValue}, $expiryNote.")
                0
            },
        )
        exitProcess(exitCode)
    }
}
