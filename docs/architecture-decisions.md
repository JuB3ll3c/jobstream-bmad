# Architecture Decisions — JobStream

Toutes les décisions d'architecture prises pour JobStream, avec le contexte, les alternatives envisagées, et les conséquences.

---

## AD-1 — Paradigme Event-Driven Microservices

**Contexte :** JobStream a deux workloads distincts : un CRUD classique (sauvegarde d'offres, gestion de CV, kanban) et un pipeline réactif I/O-bound (appels OpenAI, streaming SSE). Les mettre dans le même service aurait mélangé WebMVC et WebFlux ou imposé un compromis sur la stack.

**Décision :** Deux services Spring Boot communiquant via Kafka. dashboard-service en WebMVC/JPA pour le CRUD, ai-analyzer-service en WebFlux/R2DBC pour le reactive. Aucun appel REST direct entre services — tout passe par Kafka.

**Alternatives :**
- Monolithe Spring Boot : plus simple à déployer, mais mélange les stacks et ne montre pas la maîtrise des microservices
- Service unique avec modules : ne résout pas le conflit WebMVC/WebFlux

**Conséquences :** +complexité infra (Kafka, 2 services), +clarté architecturale, vitrine technique plus riche.

---

## AD-2 — Base de Données Partagée, Tables Isolées

**Contexte :** Les deux services ont besoin d'accéder aux mêmes données (le dashboard a besoin des résultats d'analyse, l'analyzer a besoin du CV et des offres).

**Décision :** PostgreSQL unique, mais chaque service écrit exclusivement dans ses propres tables. Le dashboard possède `saved_jobs` et `cv`. L'analyzer possède `analysis_results`. Chacun peut lire les tables de l'autre.

**Alternatives :**
- Deux bases de données séparées : plus "propre" en microservices, mais complexifie les jointures métier et ajoute de l'infra pour un projet solo
- Base unique sans règle d'ownership : risque d'accidents (un service qui écrase les données de l'autre)

**Conséquences :** Couplage faible sur le schéma (un changement de colonne dans `analysis_results` peut impacter les requêtes du dashboard). Acceptable pour un projet solo — si le projet devient multi-équipe, on migre vers deux bases.

---

## AD-3 — Deux Topics Kafka

**Contexte :** L'architecture initiale prévoyait un seul topic `analysis.request`. L'ajout de la cover letter (déclenchée séparément, après l'analyse) aurait forcé un champ `type` dans le message pour distinguer l'intention.

**Décision :** Deux topics dédiés : `analysis.request` et `cover-letter.request`. Chacun avec son consumer, sa DLT (`.DLT`), sa politique de retry.

**Alternatives :**
- Topic unique avec champ `type` : plus simple mais mélange des responsabilités, difficile d'avoir des politiques de retry différentes
- Appel REST direct pour la cover letter : casse le découplage event-driven

**Conséquences :** +2 topics à gérer, mais isolation claire des flux. Chaque topic peut évoluer indépendamment.

---

## AD-4 — Cover Letter Dépend de l'Analyse

**Contexte :** La cover letter doit être contextuelle — utiliser les points forts/faibles identifiés par l'analyse pour personnaliser le message.

**Décision :** L'analyzer vérifie l'existence d'un `analysis_results` pour le `saved_job_id` avant de traiter une demande de cover letter. Si absent, le message est rejeté vers la DLT.

**Alternatives :**
- Cover letter sans dépendance : plus simple mais génère des lettres génériques sans valeur
- Forcer l'ordre dans le front (désactiver le bouton tant que l'analyse n'est pas faite) : suffisant en UI mais pas garantie au niveau message

**Conséquences :** Sécurité au niveau du consumer. Le front peut aussi désactiver le bouton pour meilleure UX.

---

## AD-5 — Statut Kanban dans saved_jobs

**Contexte :** Le suivi des candidatures suit un cycle d'états (sauvegardé → analysé → cover letter → postulé → retour). Fallait décider où stocker cet état.

**Décision :** Une colonne `status` dans la table `saved_jobs`. Enum : `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`. Transitions forward-only. Le dashboard possède et mute ce champ.

**Alternatives :**
- Table dédiée `application_status` avec historique : plus flexible (historique complet, états custom), mais overkill pour un besoin simple
- Service dédié de state machine : prouesse technique inutile ici

**Conséquences :** Simple, un seul point de vérité. Pas d'historique des transitions — si nécessaire plus tard, on ajoute une table de log.

---

## AD-6 — CV Stocké en PostgreSQL

**Contexte :** Le CV (format markdown) doit être accessible par l'analyzer pour les appels OpenAI. Sur un PC, on pourrait le stocker en fichier, mais l'analyzer dans son conteneur Docker n'y aurait pas accès.

**Décision :** Table `cv` en PostgreSQL. Une seule ligne (un seul CV). Upload via `POST /api/cv` sur le dashboard. Contenu stocké en TEXT.

**Alternatives :**
- Fichier sur le filesystem avec volume Docker partagé : plus complexe à configurer, moins portable (différent selon OS/hébergeur)
- Object storage (S3) : infra supplémentaire injustifiée pour un projet solo

**Conséquences :** Dépendance à la DB pour un fichier. Si le CV devient volumineux (images), reconsidérer le stockage. Facile à migrer plus tard (l'interface est un upload HTTP, le backend de stockage est encapsulé).

---

## AD-7 — Cache Redis pour les Offres

**Contexte :** Les appels API vers JSearch et Adzuna ont des quotas et de la latence. Les mêmes recherches peuvent être répétées.

**Décision :** Redis en cache-aside côté dashboard-service. TTL de 30 minutes. Clé = query string. Cache miss → appel API → stockage → retour. Cache hit → retour immédiat.

**Alternatives :**
- Cache en mémoire (Caffeine) : plus simple, pas d'infra Redis, mais perdu au redémarrage du service et pas partageable
- Cache en base PostgreSQL : fonctionnel mais plus lent, Redis montre une techno supplémentaire

**Conséquences :** Infra supplémentaire (Redis), montre une compétence recherchée. Facile à retirer si le projet reste solo et les quotas suffisent.

---

## AD-8 — SSE par l'Analyzer

**Contexte :** Le frontend doit recevoir les résultats d'analyse et de cover letter en temps réel. Le choix du canal de notification.

**Décision :** L'ai-analyzer-service expose un unique endpoint SSE `GET /api/events`. Deux types d'événements : `analysis-completed` et `cover-letter-completed`. Single-instance (pas de scaling multi-instances nécessaire pour un projet solo).

**Alternatives :**
- WebSocket : bidirectionnel, plus lourd à mettre en œuvre. SSE suffit (unidirectionnel serveur → client)
- Polling HTTP : simple mais pas temps réel, gaspillage de ressources
- Dashboard comme relay : ferait transiter par un service non-réactif, ajouterait de la latence

**Conséquences :** SSE est simple, unidirectionnel, natif dans le navigateur et WebFlux. Si passage multi-instances plus tard, ajouter un topic Kafka `sse.events` comme bus.

---

## AD-9 — Dead-Letter Topic par Topic

**Contexte :** Les messages Kafka peuvent échouer (API OpenAI down, requête invalide, analyse introuvable). Sans file de reprise, les messages sont perdus silencieusement.

**Décision :** Chaque topic a un topic `.DLT` dédié. Politique de retry : 3 tentatives avec backoff exponentiel. Après épuisement, le message atterrit dans la DLT pour inspection manuelle et rejeu.

**Alternatives :**
- Retry infini : peut bloquer le consumer indéfiniment
- Log + abandon : perte de données silencieuse
- Pas de DLT (comportement Kafka par défaut) : message perdu à la première erreur

**Conséquences :** Résilience. Un message en DLT nécessite une action manuelle — acceptable pour un projet solo. Pour du multi-utilisateur, un mécanisme de rejeu automatique serait nécessaire.
