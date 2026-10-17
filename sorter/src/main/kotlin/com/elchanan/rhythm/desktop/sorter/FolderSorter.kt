package com.elchanan.rhythm.desktop.sorter

import com.elchanan.rhythm.data.db.AudioFeatureEntity
import com.elchanan.rhythm.data.db.SongEntity
import com.elchanan.rhythm.desktop.audio.Analyzer
import com.elchanan.rhythm.engine.Mood
import com.elchanan.rhythm.engine.MoodModel
import org.jaudiotagger.audio.AudioFileIO
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingWorker
import javax.swing.UIManager

private val audioExtensions = setOf(
    "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma",
    "aiff", "aif", "au", "mp2", "mp4", "m4p", "ape", "wv"
)

private data class AnalyzedTrack(val file: File, val feature: AudioFeatureEntity?)
private data class SortSummary(val copied: Int, val unreadable: Int, val distribution: Map<String, Int>)

fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    javax.swing.SwingUtilities.invokeLater { SorterWindow().isVisible = true }
}

private class SorterWindow : JFrame("מיון מוזיקה לפי קצב ואווירה") {
    private val sourceField = JTextField()
    private val targetField = JTextField()
    private val status = JLabel("בחר תיקייה עם שירים ותיקיית יעד נפרדת.", SwingConstants.RIGHT)
    private val progress = JProgressBar()
    private val startButton = JButton("מיין מוזיקה")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(680, 300)
        preferredSize = Dimension(800, 340)

        val content = JPanel(BorderLayout(10, 10))
        content.border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        content.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT

        val form = JPanel(GridLayout(2, 1, 8, 8))
        form.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        form.add(pathRow("תיקיית המקור:", sourceField))
        form.add(pathRow("תיקיית היעד:", targetField))

        val note = JLabel(
            "<html><div style='text-align:right'>הכלי מנתח את השירים ומעתיק אותם לתיקיות לפי האווירה. " +
                "הקבצים המקוריים אינם נמחקים או משתנים, וקובץ קיים לא יידרס.</div></html>",
            SwingConstants.RIGHT
        )
        note.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT

        val bottom = JPanel(BorderLayout(8, 8))
        bottom.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        actions.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        actions.add(startButton)
        bottom.add(status, BorderLayout.NORTH)
        bottom.add(progress, BorderLayout.CENTER)
        bottom.add(actions, BorderLayout.SOUTH)

        content.add(form, BorderLayout.NORTH)
        content.add(note, BorderLayout.CENTER)
        content.add(bottom, BorderLayout.SOUTH)
        content.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
        contentPane = content

        progress.minimum = 0
        progress.maximum = 100
        progress.value = 0
        startButton.addActionListener { startSorting() }
        pack()
        setLocationRelativeTo(null)
    }

    private fun pathRow(label: String, field: JTextField): JPanel {
        val row = JPanel(BorderLayout(8, 0))
        row.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        field.columns = 32
        val browse = JButton("בחירה…")
        browse.addActionListener {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = label
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.text = chooser.selectedFile.absolutePath
            }
        }
        row.add(JLabel(label, SwingConstants.RIGHT), BorderLayout.EAST)
        row.add(field, BorderLayout.CENTER)
        row.add(browse, BorderLayout.WEST)
        return row
    }

    private fun startSorting() {
        if (sourceField.text.isBlank() || targetField.text.isBlank()) {
            JOptionPane.showMessageDialog(
                this,
                "יש לבחור גם תיקיית מקור וגם תיקיית יעד.",
                "חסרות תיקיות",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        val source = File(sourceField.text.trim()).toPath().toAbsolutePath().normalize()
        val target = File(targetField.text.trim()).toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(source)) {
            JOptionPane.showMessageDialog(this, "תיקיית המקור אינה קיימת.", "שגיאה", JOptionPane.ERROR_MESSAGE)
            return
        }
        if (source == target || source.startsWith(target) || target.startsWith(source)) {
            JOptionPane.showMessageDialog(
                this,
                "יש לבחור תיקיית יעד נפרדת שאינה בתוך תיקיית המקור ואינה תיקיית האב שלה.",
                "תיקיות חופפות",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        startButton.isEnabled = false
        progress.value = 0
        status.text = "מתחיל לסרוק…"

        val worker = object : SwingWorker<SortSummary, String>() {
            override fun doInBackground(): SortSummary {
                val files = Files.walk(source).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .filter {
                            it.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions
                        }
                        .sorted()
                        .toList()
                }
                if (files.isEmpty()) {
                    throw IllegalStateException("לא נמצאו קובצי מוזיקה נתמכים בתיקיית המקור.")
                }

                val tracks = ArrayList<AnalyzedTrack>(files.size)
                var unreadable = 0
                files.forEachIndexed { index, path ->
                    publish("מנתח ${index + 1} מתוך ${files.size}: ${path.fileName}")
                    val file = path.toFile()
                    val feature = try {
                        val durationMs = runCatching {
                            AudioFileIO.read(file).audioHeader.trackLength.coerceAtLeast(0) * 1000L
                        }.getOrDefault(0L)
                        val title = file.nameWithoutExtension
                        val song = SongEntity(
                            id = file.absolutePath.hashCode().toLong() xor file.lastModified(),
                            title = title,
                            titleLower = title.lowercase(Locale.ROOT),
                            artistName = "",
                            artistKey = "",
                            albumName = "",
                            albumId = 0L,
                            durationMs = durationMs,
                            trackNumber = 0,
                            year = 0,
                            genre = null,
                            path = file.absolutePath,
                            folder = file.parent ?: "",
                            dateAddedSec = 0L,
                            sizeBytes = file.length()
                        )
                        Analyzer.analyze(song)
                    } catch (_: Exception) {
                        null
                    }
                    if (feature == null || feature.energy <= 0f) unreadable++
                    tracks.add(AnalyzedTrack(file, feature))
                }

                val features = tracks.mapNotNull { it.feature }
                val moodModel = if (features.isEmpty()) null else MoodModel(features)
                Files.createDirectories(target)
                val counts = linkedMapOf<String, Int>()
                var copied = 0

                tracks.forEachIndexed { index, track ->
                    publish("מעתיק ${index + 1} מתוך ${tracks.size}: ${track.file.name}")
                    val feature = track.feature
                    val folder = if (feature == null || feature.energy <= 0f || moodModel == null) {
                        "לא נותח"
                    } else {
                        val moods = Mood.values().toList()
                        val matched = moods.filter { moodModel.matches(it, feature) }
                        // If a song matches the energetic mood, that category wins.
                        // Each track is copied to exactly one category folder.
                        val selected = matched.firstOrNull { it == Mood.ENERGETIC }
                            ?: matched.maxByOrNull { moodModel.strength(it, feature) }
                            ?: moods.maxByOrNull { moodModel.strength(it, feature) }
                        selected?.label ?: "לא נותח"
                    }

                    val folderPath = target.resolve(folder)
                    Files.createDirectories(folderPath)
                    val destination = uniqueDestination(folderPath, track.file.name)
                    try {
                        // Do not copy file attributes: removable drives and
                        // filesystems often cannot preserve the source metadata.
                        Files.copy(track.file.toPath(), destination)
                        copied++
                        counts[folder] = (counts[folder] ?: 0) + 1
                    } catch (_: Exception) {
                        counts["שגיאת העתקה"] = (counts["שגיאת העתקה"] ?: 0) + 1
                    }
                }
                return SortSummary(copied, unreadable, counts)
            }

            override fun process(chunks: List<String>) {
                if (chunks.isNotEmpty()) status.text = chunks.last()
            }

            override fun done() {
                this@SorterWindow.progress.value = 100
                startButton.isEnabled = true
                try {
                    val result = get()
                    val breakdown = result.distribution.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    status.text = "הסתיים. הועתקו ${result.copied} קבצים."
                    JOptionPane.showMessageDialog(
                        this@SorterWindow,
                        "המיון הסתיים.\nהועתקו: ${result.copied}\nלא נותחו: ${result.unreadable}\n\n${breakdown}",
                        "המיון הושלם",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    val message = e.cause?.message ?: e.message ?: "שגיאה לא ידועה"
                    status.text = "המיון לא הושלם."
                    JOptionPane.showMessageDialog(this@SorterWindow, message, "שגיאה", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
        worker.execute()
    }

    private fun uniqueDestination(folder: Path, originalName: String): Path {
        var candidate = folder.resolve(originalName)
        if (!Files.exists(candidate)) return candidate
        val name = originalName.substringBeforeLast('.', originalName)
        val ext = originalName.substringAfterLast('.', "")
        var counter = 2
        while (Files.exists(candidate)) {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            candidate = folder.resolve("$name ($counter)$suffix")
            counter++
        }
        return candidate
    }
}
