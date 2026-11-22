package com.akynex.logicmix.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migration policy: every schema change bumps the version, adds an explicit Migration here and
// a test that opens an old-version database. Destructive fallback must never be enabled.
object LogicMixMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `progress` (`profileId` TEXT NOT NULL, `level` INTEGER NOT NULL, " +
                    "`totalXp` INTEGER NOT NULL, `streak` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileId`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `solved_puzzles` (`profileId` TEXT NOT NULL, `puzzleId` TEXT NOT NULL, " +
                    "`solvedAt` INTEGER NOT NULL, `hintsUsed` INTEGER NOT NULL, `firstSolveXp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileId`, `puzzleId`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`profileId` TEXT NOT NULL, `puzzleId` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `finishedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_profileId` ON `attempts` (`profileId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `xp_transactions` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                    "`puzzleId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) " +
                    "REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_xp_transactions_profileId` ON `xp_transactions` (`profileId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
