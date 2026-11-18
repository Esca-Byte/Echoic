package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.SongItem
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val albumName: String,
    val moodCategory: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSongItem(): SongItem = SongItem(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        albumName = albumName,
        moodCategory = moodCategory,
        isFavorite = true
    )

    companion object {
        fun fromSongItem(song: SongItem): FavoriteSongEntity = FavoriteSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            albumName = song.albumName,
            moodCategory = song.moodCategory
        )
    }
}

@Entity(tableName = "offline_songs")
data class OfflineSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    val localFilePath: String,
    val originalStreamUrl: String,
    val albumName: String,
    val moodCategory: String,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSongItem(isFavorite: Boolean = false): SongItem = SongItem(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = localFilePath.ifBlank { originalStreamUrl },
        albumName = albumName,
        moodCategory = moodCategory,
        isFavorite = isFavorite,
        isDownloaded = true,
        localFilePath = localFilePath
    )
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "imported_playlists")
data class ImportedPlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val category: String,
    val sourcePlatform: String,
    val remotePlaylistId: String,
    val searchFallbackQuery: String,
    val trackCount: Int,
    val tracksJson: String = "[]",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_played_songs")
data class RecentlyPlayedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val albumName: String,
    val moodCategory: String,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSongItem(isFavorite: Boolean = false): SongItem = SongItem(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        albumName = albumName,
        moodCategory = moodCategory,
        isFavorite = isFavorite
    )

    companion object {
        fun fromSongItem(song: SongItem): RecentlyPlayedSongEntity = RecentlyPlayedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            albumName = song.albumName,
            moodCategory = song.moodCategory,
            playedAt = System.currentTimeMillis()
        )
    }
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE id = :songId")
    suspend fun deleteFavoriteById(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    suspend fun isFavorite(songId: String): Boolean

    @Query("SELECT * FROM offline_songs ORDER BY downloadedAt DESC")
    fun getAllOfflineSongs(): Flow<List<OfflineSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineSong(song: OfflineSongEntity)

    @Query("DELETE FROM offline_songs WHERE id = :songId")
    suspend fun deleteOfflineSongById(songId: String)

    @Query("SELECT * FROM offline_songs WHERE id = :songId LIMIT 1")
    suspend fun getOfflineSongById(songId: String): OfflineSongEntity?

    @Query("SELECT * FROM recently_played_songs ORDER BY playedAt DESC LIMIT 12")
    fun getRecentlyPlayedSongs(): Flow<List<RecentlyPlayedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayedSong(song: RecentlyPlayedSongEntity)

    @Query("DELETE FROM recently_played_songs")
    suspend fun clearRecentlyPlayedSongs()

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 12")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM imported_playlists ORDER BY addedAt DESC")
    fun getAllImportedPlaylists(): Flow<List<ImportedPlaylistEntity>>

    @Query("SELECT * FROM imported_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getImportedPlaylistById(playlistId: String): ImportedPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedPlaylist(playlist: ImportedPlaylistEntity)

    @Query("DELETE FROM imported_playlists WHERE id = :playlistId")
    suspend fun deleteImportedPlaylistById(playlistId: String)
}

@Database(
    entities = [
        FavoriteSongEntity::class,
        OfflineSongEntity::class,
        RecentlyPlayedSongEntity::class,
        SearchHistoryEntity::class,
        ImportedPlaylistEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        private fun ensureAllTablesExist(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite_songs` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `artworkUrl` TEXT NOT NULL,
                    `streamUrl` TEXT NOT NULL,
                    `albumName` TEXT NOT NULL,
                    `moodCategory` TEXT NOT NULL,
                    `addedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `offline_songs` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `artworkUrl` TEXT NOT NULL,
                    `localFilePath` TEXT NOT NULL,
                    `originalStreamUrl` TEXT NOT NULL,
                    `albumName` TEXT NOT NULL,
                    `moodCategory` TEXT NOT NULL,
                    `downloadedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                    `query` TEXT NOT NULL PRIMARY KEY,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `imported_playlists` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `title` TEXT NOT NULL,
                    `subtitle` TEXT NOT NULL,
                    `artworkUrl` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `sourcePlatform` TEXT NOT NULL,
                    `remotePlaylistId` TEXT NOT NULL,
                    `searchFallbackQuery` TEXT NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `tracksJson` TEXT NOT NULL DEFAULT '[]',
                    `addedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recently_played_songs` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `artworkUrl` TEXT NOT NULL,
                    `streamUrl` TEXT NOT NULL,
                    `albumName` TEXT NOT NULL,
                    `moodCategory` TEXT NOT NULL,
                    `playedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesExist(db)
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesExist(db)
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesExist(db)
                runCatching {
                    db.execSQL("ALTER TABLE `imported_playlists` ADD COLUMN `tracksJson` TEXT NOT NULL DEFAULT '[]'")
                }
            }
        }

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "somify_music.db"
                ).addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
