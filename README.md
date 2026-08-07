# shopify-mcp-server

Serveur MCP distant du catalogue Shopify — Kotlin / Spring Boot.

Détient les credentials Shopify par boutique, valide les jetons OAuth des opérateurs, applique les
droits par boutique et journalise les opérations. Le connecteur distribué se réduit à une URL et un
manifeste : aucun secret Shopify ne réside sur le poste d'un opérateur.

- **Architecture** : `zickat-startup/projects/agentic-ecommerce/initiatives/catalog-plugin-oauth-tenancy/architecture.md`
- **Modèle de données** : `schema.md` de la même initiative
- **Tâches du lot en cours** : `tasks.md` + `tasks/LOT0-*.md`
- **Doctrine** : `guidelines/backend.md`, `testing.md`, `security.md` du dépôt `zickat-startup`

Ce dépôt est la cible de la transition décrite en §4 de l'architecture. Le dépôt
`mcp-shopify-catalog` (TypeScript) reste la source de vérité du comportement pendant toute la
transition, et n'est archivé qu'au dernier lot.
