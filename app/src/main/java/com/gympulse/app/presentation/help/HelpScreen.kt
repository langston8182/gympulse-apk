package com.gympulse.app.presentation.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.IconBadge

/** One help topic: a title and a few short steps or tips. */
private data class HelpTopic(val icon: ImageVector, val title: String, val summary: String, val steps: List<String>)

private val topics = listOf(
    HelpTopic(
        Icons.Rounded.RocketLaunch,
        "Bien démarrer",
        "Votre première séance en 4 étapes",
        listOf(
            "Connectez-vous avec Google : vos données vous suivent sur tous vos appareils.",
            "Onglet Séances → « Nouvelle séance » : donnez un nom et choisissez un objectif (hypertrophie, force-hypertrophie, force, endurance).",
            "« Ajouter des exercices » : choisissez-les dans la bibliothèque. Les séries, répétitions et temps de repos par défaut dépendent de l'objectif et se modifient ensuite.",
            "Touchez « Enregistrer ». Votre séance apparaît sur l'accueil : touchez « Démarrer l'entraînement ».",
        ),
    ),
    HelpTopic(
        Icons.AutoMirrored.Rounded.EventNote,
        "Créer et organiser mes séances",
        "Séries, charges, repos, ordre, favoris",
        listOf(
            "Chaque exercice a ses séries : répétitions, charge et repos. Touchez « Ajouter une série » pour en ajouter, la corbeille d'une série pour la retirer.",
            "Le temps de repos se règle par exercice (et peut être différent pour une série précise).",
            "Pour changer l'ordre des exercices d'une séance, utilisez les flèches de l'exercice ou le mode de réorganisation.",
            "Vos séances sont rangées par lieu (le lieu actuel en premier). Une séance liée à plusieurs lieux apparaît dans chacun ; sans lieu associé, elle est dans « Sans lieu ».",
            "Pour classer vos séances : onglet Séances → « Réorganiser mes séances », puis maintenez la poignée et glissez, lieu par lieu. Cet ordre est repris sur l'accueil.",
            "L'étoile met une séance en favori ; « Dupliquer » en crée une copie à adapter.",
        ),
    ),
    HelpTopic(
        Icons.Rounded.FitnessCenter,
        "Les exercices",
        "Bibliothèque fournie et vos propres exercices",
        listOf(
            "La bibliothèque contient des exercices prêts à l'emploi, classés Push, Pull ou Legs (couleur du pictogramme et filtres).",
            "Pour créer le vôtre : Séances → Exercices → « Nouvel exercice ».",
            "Si vous modifiez un exercice fourni, GymPulse crée votre copie et la met à la place de l'original dans vos séances. Si vous la supprimez, l'original revient.",
            "Charge « par haltère » : saisissez le poids d'un seul haltère, le volume compte les deux.",
            "Vos photos personnelles restent sur ce téléphone et ne sont pas synchronisées.",
        ),
    ),
    HelpTopic(
        Icons.Rounded.PlayCircle,
        "Faire une séance",
        "Valider les séries, repos automatique, pause",
        listOf(
            "Touchez « Terminer la série » après chaque série : saisissez au besoin les répétitions et la charge réalisées.",
            "Le repos se lance automatiquement avec un minuteur. Vous pouvez l'allonger ou le passer.",
            "Vous êtes alerté à la fin du repos, même écran verrouillé (notification, son, vibration selon vos réglages).",
            "Pause, ordre des exercices restants, série supplémentaire, notes : touchez « Plus d'actions ».",
            "Si l'app est fermée en pleine séance, elle la retrouve à la réouverture : rien n'est perdu.",
            "« Terminer la séance » l'enregistre dans votre historique, avec un résumé et vos records. La séance terminée passe à la fin de votre liste : sur l'accueil, la séance suivante arrive en première position (rien ne change si vous abandonnez la séance).",
        ),
    ),
    HelpTopic(
        Icons.Rounded.Insights,
        "Progression et records",
        "Volume, 1RM estimé, historique",
        listOf(
            "Onglet Progression : volume, fréquence, évolution par exercice, 1RM estimé et records.",
            "Un record est détecté par rapport à vos performances précédentes (charge, répétitions, durée, volume). Une première performance n'est jamais un record.",
            "Touchez une séance de l'historique pour revoir son détail série par série ; vous pouvez la supprimer.",
            "Le 1RM est toujours une estimation (formule d'Epley, de 1 à 12 répétitions).",
        ),
    ),
    HelpTopic(
        Icons.Rounded.Place,
        "Lieux et suggestions",
        "Salle, maison, matériel disponible",
        listOf(
            "Profil → Lieux d'entraînement : créez vos lieux avec une adresse, un point sur la carte (touchez-la) ou votre position actuelle, un rayon et le matériel disponible.",
            "Associez des séances à un lieu : l'accueil propose seulement celles de l'endroit sélectionné (ou celles qui ne sont liées à aucun lieu et dont le matériel convient), et vous pouvez glisser vers les autres. Une séance réservée à un autre lieu n'est jamais proposée.",
            "À chaque ouverture, GymPulse détecte où vous êtes (autorisez la localisation au premier lancement) et l'accueil affiche les séances de ce lieu.",
            "Si rien n'est détecté, ou pour forcer un autre lieu, choisissez-le à la main avec la pastille en haut de l'écran : il est conservé jusqu'à ce qu'un lieu soit détecté à l'ouverture suivante.",
            "La position est lue seulement à l'ouverture de l'app, jamais en arrière-plan, et n'est pas envoyée au serveur.",
        ),
    ),
    HelpTopic(
        Icons.Rounded.CloudSync,
        "Compte et synchronisation",
        "Hors ligne, appareils, suppression",
        listOf(
            "GymPulse fonctionne sans réseau : vos modifications sont envoyées dès que la connexion revient.",
            "Paramètres → Synchronisation montre l'état, le nombre de modifications en attente et « Synchroniser maintenant ».",
            "Sur un autre téléphone, connectez-vous avec le même compte Google pour retrouver vos données.",
            "« Se déconnecter » efface les données de ce téléphone (elles restent sur votre compte).",
            "« Supprimer mon compte » efface définitivement votre compte et toutes vos données.",
        ),
    ),
    HelpTopic(
        Icons.Rounded.Timer,
        "Réglages utiles",
        "Unités, repos, son, écran",
        listOf(
            "Paramètres : thème, kg ou lb, repos par défaut, son, vibrations, écran allumé pendant la séance et objectif hebdomadaire.",
            "Le prénom affiché sur l'accueil se change dans Profil (touchez votre nom).",
        ),
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(topBar = { EvoTopBar(title = "Aide", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Touchez un sujet pour afficher l'explication.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(topics, key = { it.title }) { topic -> TopicCard(topic) }
            item {
                Text(
                    "Une question ou un souci ? Écrivez à cyril.marchive@gmail.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TopicCard(topic: HelpTopic) {
    var expanded by rememberSaveable(topic.title) { mutableStateOf(false) }
    EvoCard(onClick = { expanded = !expanded }, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(topic.icon, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium)
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Réduire" else "Développer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                topic.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
