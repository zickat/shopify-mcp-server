package com.zickat.shopifymcpserver.vault

/**
 * LOT2-09 (déploiement, `catalog-plugin-oauth-tenancy`) — écrit le `STORE_CREDENTIAL` réel d'une
 * boutique, à l'usage exclusif de Val : jamais un agent, jamais un fichier committé, jamais un
 * journal (contrat `LOT2-09`, `progress.md`). Le pendant non secret, `STORE`, est une commande
 * séparée dans le module `tenancy` (`SeedStoresRunner`) — à lancer d'abord.
 *
 * Gardée derrière le profil Spring `seed`, absent de tout profil par défaut — jamais active sur le
 * serveur qui sert du trafic. Placée dans le module `vault` (pas un module `tooling` séparé) : seul
 * moyen d'appeler `StoreCredentialUseCase.store` (chiffrement enveloppe AES-GCM, AAD = storeId — D18)
 * sans réimplémenter le chiffrement ailleurs — un script shell/mongosh qui le referait serait
 * précisément le risque que `schema.md` §4 signale, une seule anicroche y rendrait un credential réel
 * illisible. `ModularityTests.verifies module structure` refuse qu'un module externe touche
 * `vault.domain.StoreCredentialUseCase` (pas dans `exposed_interface`) — vérifié par un run réel du
 * test. Exception documentée au périmètre d'écriture du DevOps (qui ne touche normalement pas
 * `src/`) : décidé nécessaire plutôt que d'étendre `VaultExposedService` d'une méthode d'écriture,
 * décision plus lourde qu'un script de semis ne devrait trancher seul.
 *
 * `ShopifyCredential` (le format `{"apiKey","apiSecret","shopDomain"}` défini en `LOT2-01`) vit dans
 * le module `shopify`, que `vault` ne dépend PAS de (c'est l'inverse : `shopify` dépend de `vault`).
 * Cette commande construit donc son propre petit DTO local, identique champ pour champ — duplication
 * volontaire et inoffensive d'une forme de données, pas du chiffrement, pour ne pas inverser le sens
 * de dépendance D23 pour un script opérationnel.
 *
 * Usage (terminal interactif obligatoire — jamais de pipe, jamais un argument, jamais une variable
 * d'environnement pour les secrets, voir le corps de la commande) :
 *   java -jar app.jar --spring.profiles.active=seed --seed.command=credential --seed.slug=velotrip
 */

import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import kotlin.system.exitProcess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Même liste que `tenancy.SeedStoresRunner.KNOWN_STORES` — dupliquée volontairement (deux modules,
 * pas de dépendance croisée pour une donnée non secrète). Voir ce fichier pour la source de chaque
 * domaine. */
private val KNOWN_STORE_DOMAINS = mapOf(
    "velotrip" to "bikepacking-9180.myshopify.com",
    "lurelab" to "cepvte-eu.myshopify.com",
)

/** Reproduction locale du format `LOT2-01` (`shopify.domain.models.ShopifyCredential`), module
 * `shopify` non accessible ici (D23 — `shopify` dépend de `vault`, jamais l'inverse). Champs et noms
 * de clé JSON identiques, volontairement : c'est ce que `ShopifyAdminGatewayImpl` lira au déchiffrement. */
@Serializable
private data class SeedShopifyCredentialPlaintext(
    @SerialName("apiKey") val apiKey: String,
    @SerialName("apiSecret") val apiSecret: String,
    @SerialName("shopDomain") val shopDomain: String,
)

private val plaintextJson = Json { ignoreUnknownKeys = true }

private fun UseCaseError.describe(): String = (this as? DomainError)?.messageKey ?: this::class.simpleName ?: "erreur inconnue"

@Component
@Profile("seed")
@Order(2)
class SeedCredentialRunner(
    private val storeCredentialUseCase: StoreCredentialUseCase,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (args.getOptionValues("seed.command")?.firstOrNull() != "credential") return

        val slug = args.getOptionValues("seed.slug")?.firstOrNull()
        val storeIdArg = args.getOptionValues("seed.storeId")?.firstOrNull()
        if (slug == null || storeIdArg == null) {
            System.err.println(
                "--seed.slug=<velotrip|lurelab> et --seed.storeId=<id retourné par --seed.command=stores> " +
                    "sont obligatoires. Relire la sortie de --seed.command=stores pour l'id exact."
            )
            exitProcess(1)
        }
        val shopDomain = KNOWN_STORE_DOMAINS[slug]
        if (shopDomain == null) {
            System.err.println("Slug inconnu : '$slug' (attendu : velotrip ou lurelab).")
            exitProcess(1)
        }

        val console = System.console()
        if (console == null) {
            System.err.println(
                "Aucun terminal interactif attaché (System.console() est null) — cette commande refuse " +
                    "de lire un secret depuis un flux non interactif (pipe/redirection), où la saisie ne " +
                    "peut pas être masquée. Lancer ce jar directement dans un terminal."
            )
            exitProcess(1)
        }

        println("Boutique '$slug' ($shopDomain) — saisie des credentials Admin API Shopify.")
        println("Rien de ce qui suit n'est jamais journalisé ni affiché en clair sur ce terminal.")
        val apiKey = console.readPassword("  apiKey (masqué) : ")
        val apiSecret = console.readPassword("  apiSecret (masqué) : ")
        if (apiKey == null || apiSecret == null || apiKey.isEmpty() || apiSecret.isEmpty()) {
            System.err.println("apiKey et apiSecret sont obligatoires — abandon, rien n'a été écrit en base.")
            exitProcess(1)
        }
        val scopesGranted = console.readLine(
            "  scopes accordés (Shopify admin -> Settings -> Apps -> cette app -> API credentials, texte libre, jamais un secret) : "
        ) ?: ""

        val plaintext = plaintextJson
            .encodeToString(
                SeedShopifyCredentialPlaintext.serializer(),
                SeedShopifyCredentialPlaintext(apiKey = String(apiKey), apiSecret = String(apiSecret), shopDomain = shopDomain),
            )
            .toByteArray()
        apiKey.fill(' ')
        apiSecret.fill(' ')

        val exitCode = storeCredentialUseCase.store(storeIdArg, plaintext, scopesGranted).fold(
            { err ->
                System.err.println("Échec d'écriture de STORE_CREDENTIAL pour '$slug' : ${err.describe()}")
                1
            },
            { id ->
                println("STORE_CREDENTIAL créé pour '$slug' — id=${id.value} (jamais la valeur des secrets).")
                0
            },
        )
        exitProcess(exitCode)
    }
}
