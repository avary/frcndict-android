package com.nilhcem.frcndict.database;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import com.nilhcem.frcndict.settings.SettingsActivity;
import com.nilhcem.frcndict.utils.ChineseCharsHandler;
import com.nilhcem.frcndict.utils.Log;

public final class DatabaseHelper {
	public static final String DATABASE_NAME = "dictionary.db";
	public static final String VERSION_SEPARATOR = "-";
	private static final DatabaseHelper INSTANCE = new DatabaseHelper();
	private static final String TAG = "DatabaseHelper";

	private int mUsed = 0;
	private File mDbPath;
	private SQLiteDatabase mDb;

	private static final String QUERY_IS_PINYIN;
	private static final String QUERY_GET_ID_BY_HANZI;
	private static final String QUERY_HANZI;
	private static final String QUERY_PINYIN;
	private static final String QUERY_FRENCH;
	private static final String QUERY_FRENCH_NO_ACCENT;
	private static final String QUERY_STARRED;
	private static final String[] COLUMNS_FIND_BY_ID;
	private static final String[] COLUMNS_GET_ALL_STARRED;
	private static final String LIMIT_1;

	private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
	private static final String REGEX_ACCENT = ".*[àâäçéèêëîïôöùûüæœÀÂÄÇÉÈÊËÎÏÔÖÙÛÜÆŒ].*";

	static {
		StringBuilder sb;

		// Query for detecting if input is pinyin or french
		sb = new StringBuilder("SELECT `")
			.append(Tables.ENTRIES_KEY_ROWID)
			.append("` FROM `")
			.append(Tables.ENTRIES_TABLE_NAME)
			.append("` WHERE `")
			.append(Tables.ENTRIES_KEY_PINYIN2)
			.append("` GLOB '%s*'");
		QUERY_IS_PINYIN = sb.toString();

		// Query for getting id by Hanzi
		sb = new StringBuilder("SELECT `")
			.append(Tables.ENTRIES_KEY_ROWID)
			.append("` FROM `")
			.append(Tables.ENTRIES_TABLE_NAME)
			.append("` WHERE `")
			.append(Tables.ENTRIES_KEY_SIMPLIFIED)
			.append("` = '%s' OR `")
			.append(Tables.ENTRIES_KEY_TRADITIONAL)
			.append("` = '%s' ORDER BY `")
			.append(Tables.ENTRIES_KEY_ROWID)
			.append("` ASC LIMIT 1");
		QUERY_GET_ID_BY_HANZI = sb.toString();

		// add 1 entry we won't display but which is just to know if there are still some elements after.
		String nbToDisplay = Integer.toString(SettingsActivity.NB_ENTRIES_PER_LIST + 1);
		StringBuilder selectAllFromWhere = new StringBuilder("SELECT `")
			.append(Tables.ENTRIES_KEY_ROWID).append("`, `")
			.append(Tables.ENTRIES_KEY_SIMPLIFIED).append("`, `")
			.append(Tables.ENTRIES_KEY_TRADITIONAL).append("`, `")
			.append(Tables.ENTRIES_KEY_PINYIN).append("`, `")
			.append(Tables.ENTRIES_KEY_TRANSLATION).append("` FROM `")
			.append(Tables.ENTRIES_TABLE_NAME)
			.append("` WHERE ");

		// Hanzi query
		sb = new StringBuilder(selectAllFromWhere)
			.append("(`")
			.append(Tables.ENTRIES_KEY_SIMPLIFIED)
			.append("` LIKE ? OR `")
			.append(Tables.ENTRIES_KEY_TRADITIONAL)
			.append("` LIKE ?) ORDER BY length(`")
			.append(Tables.ENTRIES_KEY_SIMPLIFIED)
			.append("`) ASC, `")
			.append(Tables.ENTRIES_KEY_SIMPLIFIED)
			.append("` ASC LIMIT ?,")
			.append(nbToDisplay);
		QUERY_HANZI = sb.toString();

		// Pinyin query
		sb = new StringBuilder(selectAllFromWhere)
			.append("`")
			.append(Tables.ENTRIES_KEY_PINYIN2)
			.append("` LIKE ? AND lower(`")
			.append(Tables.ENTRIES_KEY_PINYIN)
			.append("`) LIKE ? ORDER BY length(`")
			.append(Tables.ENTRIES_KEY_PINYIN)
			.append("`) ASC, `")
			.append(Tables.ENTRIES_KEY_PINYIN2)
			.append("` ASC LIMIT ?,")
			.append(nbToDisplay);
		QUERY_PINYIN = sb.toString();

		// French query
		sb = new StringBuilder("'/' || lower(`")
			.append(Tables.ENTRIES_KEY_TRANSLATION)
			.append("`) LIKE ? ORDER BY `")
			.append(Tables.ENTRIES_KEY_TRANS_AVG_LENGTH)
			.append("` ASC, `")
			.append(Tables.ENTRIES_KEY_ROWID)
			.append("` ASC LIMIT ?,")
			.append(nbToDisplay);
		QUERY_FRENCH = new StringBuilder(selectAllFromWhere).append(sb).toString();
		QUERY_FRENCH_NO_ACCENT = new StringBuilder(selectAllFromWhere)
			.append(sb.toString().replaceAll(Tables.ENTRIES_KEY_TRANSLATION,
					Tables.ENTRIES_KEY_TRANS_NO_ACCENT)).toString();

		// Starred query
		sb = new StringBuilder(selectAllFromWhere)
			.append("`")
			.append(Tables.ENTRIES_KEY_STARRED_DATE)
			.append("` IS NOT NULL ORDER BY `")
			.append(Tables.ENTRIES_KEY_STARRED_DATE)
			.append("` DESC LIMIT ?,")
			.append(nbToDisplay);
		QUERY_STARRED = sb.toString();

		// Columns for query "Find by id"
		COLUMNS_FIND_BY_ID = new String[] {
			Tables.ENTRIES_KEY_SIMPLIFIED,
			Tables.ENTRIES_KEY_TRADITIONAL,
			Tables.ENTRIES_KEY_PINYIN,
			Tables.ENTRIES_KEY_TRANSLATION,
			Tables.ENTRIES_KEY_STARRED_DATE
		};

		// Columns for query "Get All Starred"
		COLUMNS_GET_ALL_STARRED = new String[] {
			Tables.ENTRIES_KEY_SIMPLIFIED,
			Tables.ENTRIES_KEY_STARRED_DATE
		};

		LIMIT_1 = "1";
	}

	private DatabaseHelper() {
    }

	public static DatabaseHelper getInstance() {
		return INSTANCE;
    }

	public File getDatabasePath() {
		return mDbPath;
	}

	public void setDatabasePath(File dbPath) {
		mDbPath = dbPath;
	}

	// "start using database"
	public synchronized boolean open() {
		boolean success = false;
		Log.i(DatabaseHelper.TAG, "[Open] Database currently used by %d process[es]", mUsed);
		if (mDbPath == null) {
			Log.e(TAG, "mDbPath is null");
		} else if (!mDbPath.isFile()) {
			Log.e(TAG, "mDbPath doesn't exist or is not a file");
		} else {
			// Should not happen, just to make sure
			if (mDb == null) {
				mUsed = 0;
			}
			if (++mUsed == 1) {
				mDb = SQLiteDatabase.openDatabase(mDbPath.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
			}
			success = true;
		}
		return success;
	}

	// "stop using database"
	public synchronized void close() {
		if (--mUsed <= 0 && mDb != null) {
			mDb.close();
		}
		Log.i(DatabaseHelper.TAG, "[Close] Database still used by %d process[es]", mUsed);
	}

	public Cursor findById(int id) {
		return mDb.query(Tables.ENTRIES_TABLE_NAME, DatabaseHelper.COLUMNS_FIND_BY_ID,
				String.format("`%s`=%d", Tables.ENTRIES_KEY_ROWID, id), null, null, null, null, DatabaseHelper.LIMIT_1);
	}

	public int getIdByHanzi(String hanzi) {
		int id = 0;
		Cursor c = mDb.rawQuery(String.format(DatabaseHelper.QUERY_GET_ID_BY_HANZI, hanzi, hanzi), null);
		if (c.moveToFirst()) {
			id = c.getInt(c.getColumnIndex(Tables.ENTRIES_KEY_ROWID));
		}
		c.close();
		return id;
	}

	/**
	 * Returns the database version, or {@code null} if database is not initialized.
	 */
	public String getDbVersion() {
		String dbVersion = null;

		if (mDbPath != null) {
			Cursor c = null;
			try {
				c = mDb.query(Tables.METADATA_TABLE_NAME, new String[] {Tables.METADATA_KEY_VERSION},
						null, null, null, null, null, DatabaseHelper.LIMIT_1);
				if (c.moveToFirst()) {
					dbVersion = c.getString(c.getColumnIndex(Tables.METADATA_KEY_VERSION));
				}
			} catch (SQLiteException e) {
				dbVersion = null;
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}
		return dbVersion;
	}

	public Cursor searchHanzi(String search, Integer curPage) {
		String criteria = String.format("%%%s%%", search);
		return mDb.rawQuery(DatabaseHelper.QUERY_HANZI,
			new String[] {criteria, criteria,
				Integer.toString(SettingsActivity.NB_ENTRIES_PER_LIST * curPage)
			});
	}

	public Cursor searchPinyin(String search, Integer curPage) {
		String curSearch = ChineseCharsHandler.getInstance().pinyinTonesToNb(search);
		return mDb.rawQuery(DatabaseHelper.QUERY_PINYIN,
			new String[] {
				String.format("%%%s%%", curSearch.replaceAll("[^a-zA-Z]", "")),
				convertToQueryReadyPinyin(curSearch),
				Integer.toString(SettingsActivity.NB_ENTRIES_PER_LIST * curPage)
			});
	}

	public Cursor searchFrench(String search, Integer curPage) {
		// Detect query (with or without accents)
		String query;
		if (Pattern.matches(DatabaseHelper.REGEX_ACCENT, search)) {
			query = DatabaseHelper.QUERY_FRENCH;
		} else {
			query = DatabaseHelper.QUERY_FRENCH_NO_ACCENT;
		}

		return mDb.rawQuery(query, new String[] {String.format("%%/%s%%", search),
			Integer.toString(SettingsActivity.NB_ENTRIES_PER_LIST * curPage)
		});
	}

	public Cursor searchStarred(Integer curPage) {
		return mDb.rawQuery(DatabaseHelper.QUERY_STARRED,
			new String[] {
				Integer.toString(SettingsActivity.NB_ENTRIES_PER_LIST * curPage)
			});
	}

	// Checks if search is a pinyin or a french search
	public boolean isPinyin(String search) {
		boolean isPinyin = false;

		String formattedSearch = ChineseCharsHandler.getInstance().pinyinTonesToNb(search).replaceAll("[^a-zA-Z]", "");
		if (!TextUtils.isEmpty(formattedSearch.trim())) {
			Cursor c = mDb.rawQuery(String.format(DatabaseHelper.QUERY_IS_PINYIN, formattedSearch), null);
			isPinyin = (c.getCount() > 0);
			c.close();
		}
		return isPinyin;
	}

	private String convertToQueryReadyPinyin(String pinyin) {
		boolean prevCharWasSpace = false;
		boolean prevCharWasTone = false;

		StringBuilder newPinyin = new StringBuilder();

		for (char ch : pinyin.toCharArray()) {
			if (ch == ' ' && prevCharWasSpace) {
				continue;
			}

			if (ch == ' ') {
				prevCharWasSpace = true;
				if (!prevCharWasTone) {
					newPinyin.append("%");  // No % for a character before a space (ex "%n%i3 %", not "%n%i3% %")
				}
			} else {
				if (ch != ':' && (ch < '1' || ch > '5')) { // No % for a character before a tone (ex "%h%a%o3%", not "%h%a%o%3%")
					newPinyin.append("%");
				}
				prevCharWasSpace = false;
			}
			newPinyin.append(ch);
		}
		newPinyin.append("%");
		return newPinyin.toString();
	}

	public synchronized void setStarredDate(int id, Date starredDate) {
		ContentValues values = new ContentValues();
		if (starredDate == null) {
			values.put(Tables.ENTRIES_KEY_STARRED_DATE, (String) null);
		} else {
			values.put(Tables.ENTRIES_KEY_STARRED_DATE, mDateFormat.format(starredDate));
		}
		String whereClause = String.format("`%s`=%d", Tables.ENTRIES_KEY_ROWID, id);

		mDb.update(Tables.ENTRIES_TABLE_NAME, values, whereClause, null);
	}

	public void setStarredDate(String simplified, String starredDate) {
		ContentValues values = new ContentValues();
		values.put(Tables.ENTRIES_KEY_STARRED_DATE, starredDate);

		mDb.update(Tables.ENTRIES_TABLE_NAME, values,
				String.format("`%s` like ?", Tables.ENTRIES_KEY_SIMPLIFIED),
				new String[] {simplified});
	}

	public long getNbStarred() {
		long nbStarred;

		if (mDb == null) {
			nbStarred = 0;
		} else {
			// Create query
			StringBuilder query = new StringBuilder("SELECT count(`")
				.append(Tables.ENTRIES_KEY_ROWID)
				.append("`) FROM `")
				.append(Tables.ENTRIES_TABLE_NAME)
				.append("` WHERE `").append(Tables.ENTRIES_KEY_STARRED_DATE)
				.append("` IS NOT NULL");

			SQLiteStatement statement = mDb.compileStatement(query.toString());
			nbStarred = statement.simpleQueryForLong();
		}
		return nbStarred;
	}

	public Cursor getAllStarred() {
		return mDb.query(Tables.ENTRIES_TABLE_NAME, DatabaseHelper.COLUMNS_GET_ALL_STARRED,
				String.format("`%s` IS NOT NULL", Tables.ENTRIES_KEY_STARRED_DATE), null, null, null, null);
	}

	public SimpleDateFormat getDateFormat() {
		return mDateFormat;
	}

	public void beginTransaction() {
		Log.d(DatabaseHelper.TAG, "[Transaction] Begin");
		mDb.beginTransaction();
	}

	public void setTransactionSuccessfull() {
		Log.d(DatabaseHelper.TAG, "[Transaction] Success");
		mDb.setTransactionSuccessful();
	}

	public void endTransaction() {
		Log.d(DatabaseHelper.TAG, "[Transaction] End");
		mDb.endTransaction();
	}
}
