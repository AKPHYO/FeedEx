/**
 * FeedEx
 *
 * Copyright (c) 2012-2013 Frederic Julian
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 *     Permission is hereby granted, free of charge, to any person obtaining a copy
 *     of this software and associated documentation files (the "Software"), to deal
 *     in the Software without restriction, including without limitation the rights
 *     to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *     copies of the Software, and to permit persons to whom the Software is
 *     furnished to do so, subject to the following conditions:
 *
 *     The above copyright notice and this permission notice shall be included in
 *     all copies or substantial portions of the Software.
 *
 *     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *     AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *     LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *     THE SOFTWARE.
 */

package net.fred.feedex.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.os.Handler;

import net.fred.feedex.MainApplication;
import net.fred.feedex.R;
import net.fred.feedex.parser.OPML;
import net.fred.feedex.provider.FeedData.EntryColumns;
import net.fred.feedex.provider.FeedData.FeedColumns;
import net.fred.feedex.provider.FeedData.FilterColumns;

import java.io.File;

class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "FeedEx.db";
    private static final int DATABASE_VERSION = 3;

    private static final String BACKUP_OPML = Environment.getExternalStorageDirectory() + "/FeedEx_auto_backup.opml";

    private static final String ALTER_TABLE = "ALTER TABLE ";
    private static final String ADD = " ADD ";

    private final Handler mHandler;

    public DatabaseHelper(Handler handler, Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mHandler = handler;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(createTable(FeedColumns.TABLE_NAME, FeedColumns.COLUMNS, FeedColumns.TYPES));
        database.execSQL(createTable(FilterColumns.TABLE_NAME, FilterColumns.COLUMNS, FilterColumns.TYPES));
        database.execSQL(createTable(EntryColumns.TABLE_NAME, EntryColumns.COLUMNS, EntryColumns.TYPES));

        // Check if we need to import the backup
        File backupFile = new File(BACKUP_OPML);
        final boolean hasBackup = backupFile.exists();
        mHandler.post(new Runnable() { // In order to it after the database is created
            @Override
            public void run() {
                new Thread(new Runnable() { // To not block the UI
                    @Override
                    public void run() {
                        try {
                            if (hasBackup) {
                                // Perform an automated import of the backup
                                OPML.importFromFile(BACKUP_OPML);
                            } else {
                                // No database and no backup, automatically add the default feeds
                                OPML.importFromFile(MainApplication.getAppContext().getResources().openRawResource(R.raw.default_feeds));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            }
        });
    }

    public void exportToOPML() {
        try {
            OPML.exportToFile(BACKUP_OPML);
        } catch (Exception ignored) {
        }
    }

    private String createTable(String tableName, String[] columns, String[] types) {
        if (tableName == null || columns == null || types == null || types.length != columns.length || types.length == 0) {
            throw new IllegalArgumentException("Invalid parameters for creating table " + tableName);
        } else {
            StringBuilder stringBuilder = new StringBuilder("CREATE TABLE ");

            stringBuilder.append(tableName);
            stringBuilder.append(" (");
            for (int n = 0, i = columns.length; n < i; n++) {
                if (n > 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(columns[n]).append(' ').append(types[n]);
            }
            return stringBuilder.append(");").toString();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            executeCatchedSQL(
                    database,
                    ALTER_TABLE + FeedColumns.TABLE_NAME + ADD + FeedColumns.REAL_LAST_UPDATE + ' ' + FeedData.TYPE_DATE_TIME);
        }
        if (oldVersion < 3) {
            executeCatchedSQL(
                    database,
                    ALTER_TABLE + FeedColumns.TABLE_NAME + ADD + FeedColumns.RETRIEVE_FULLTEXT + ' ' + FeedData.TYPE_BOOLEAN);
        }
    }

    private void executeCatchedSQL(SQLiteDatabase database, String query) {
        try {
            database.execSQL(query);
        } catch (Exception ignored) {
        }
    }
}
