package com.zickat.shopifymcpserver.shared_kernel

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.mongodb.MongoDBContainer

/**
 * `testing.md` : « Testcontainers : MongoDB container via `WithMongoDBContainer` (trait Kotlin) ».
 *
 * **Patron « conteneur singleton »** : pas de `@Testcontainers`/`@Container` — constaté à
 * l'exécution que ces deux annotations, combinées à un champ dans un `companion object` Kotlin
 * (`@JvmStatic val`), ne le traitent **pas** comme statique/partagé (la réflexion Java de
 * l'extension JUnit voit le champ porté par `Companion`, pas littéralement `static`) : chaque
 * classe de test redémarrait un **nouveau** conteneur, et le précédent était arrêté en cours de
 * route — la cause exacte des `MongoSocketOpenException`/« Connexion refusée » observées lors de
 * la première exécution de la suite `LOT0-03`. Un simple champ annoté `@ServiceConnection`,
 * démarré une fois dans l'initialiseur (documenté par Spring comme le patron du « conteneur
 * partagé »), n'a pas ce problème : un seul conteneur pour toute la JVM de test.
 *
 * **Version d'image : `mongo:7.0`**, pinnée en version exacte (pas `:latest`). Aucune version
 * n'est prescrite par les guidelines (`testing.md` nomme le mécanisme, pas l'image) — 7.0 est la
 * dernière branche majeure stable au 2026-08-07, et le pilote `mongodb-driver-kotlin-sync`
 * (`mongodb.version=5.8.0`, géré par le BOM Boot 4.1, D14) supporte les serveurs MongoDB de 3.6 à
 * 8.0 : aucun risque d'incompatibilité driver/serveur avec ce choix.
 */
abstract class WithMongoDBContainer {
    companion object {
        @JvmStatic
        @ServiceConnection
        val mongoContainer: MongoDBContainer = MongoDBContainer("mongo:7.0").apply { start() }
    }
}
