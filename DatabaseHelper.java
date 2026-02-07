package com.example.cs360project2_mccormack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;

/**
 * DatabaseHelper - Secure SQLite Database Manager
 *
 * FIXED - Normalized schema to 3NF with categories table
 * FIXED - BCrypt password hashing (no plain text storage)
 * FIXED - SQL parameterization to prevent injection
 * FIXED - try-catch-finally blocks for resource cleanup
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DB_NAME = "project2.db";
    private static final int DB_VERSION = 2;

    // Table names
    private static final String TABLE_USERS = "users";
    private static final String TABLE_CATEGORIES = "categories";  // FIXED - added for 3NF normalization
    private static final String TABLE_INVENTORY = "inventory";

    // Column names
    private static final String COL_USER_ID = "id";
    private static final String COL_USERNAME = "username";
    private static final String COL_PASSWORD_HASH = "password_hash";  // FIXED - renamed from "password"
    private static final String COL_CATEGORY_ID = "id";
    private static final String COL_CATEGORY_NAME = "name";
    private static final String COL_INVENTORY_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_CATEGORY_FK = "category_id";  // FIXED - foreign key to categories
    private static final String COL_QUANTITY = "quantity";

    private static final int BCRYPT_WORK_FACTOR = 12;  // FIXED - BCrypt cost factor

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // FIXED - wrapped in try-catch for defensive programming
        try {
            db.execSQL("PRAGMA foreign_keys = ON;");

            // FIXED - password_hash column instead of password
            String createUsersTable = "CREATE TABLE " + TABLE_USERS + " ("
                    + COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_USERNAME + " TEXT NOT NULL UNIQUE, "
                    + COL_PASSWORD_HASH + " TEXT NOT NULL)";
            db.execSQL(createUsersTable);

            // FIXED - new categories table for 3NF normalization
            String createCategoriesTable = "CREATE TABLE " + TABLE_CATEGORIES + " ("
                    + COL_CATEGORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_CATEGORY_NAME + " TEXT NOT NULL UNIQUE)";
            db.execSQL(createCategoriesTable);

            // FIXED - inventory table with foreign key to categories
            String createInventoryTable = "CREATE TABLE " + TABLE_INVENTORY + " ("
                    + COL_INVENTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_TITLE + " TEXT NOT NULL, "
                    + COL_DESCRIPTION + " TEXT, "
                    + COL_CATEGORY_FK + " INTEGER, "
                    + COL_QUANTITY + " INTEGER DEFAULT 0, "
                    + "FOREIGN KEY (" + COL_CATEGORY_FK + ") REFERENCES "
                    + TABLE_CATEGORIES + "(" + COL_CATEGORY_ID + ") ON DELETE SET NULL)";
            db.execSQL(createInventoryTable);

            db.execSQL("CREATE INDEX idx_inventory_category ON "
                    + TABLE_INVENTORY + "(" + COL_CATEGORY_FK + ")");

            Log.i(TAG, "Database tables created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating database tables: " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
        } catch (Exception e) {
            Log.e(TAG, "Error upgrading database: " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    // FIXED - BCrypt password hashing, parameterized query, try-catch-finally
    public boolean addUser(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getWritableDatabase();

            // FIXED - parameterized query prevents SQL injection
            cursor = db.rawQuery(
                    "SELECT " + COL_USER_ID + " FROM " + TABLE_USERS + " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim()}
            );

            if (cursor.getCount() > 0) {
                return false;
            }

            // FIXED - hash password with BCrypt before storage
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_WORK_FACTOR));

            ContentValues values = new ContentValues();
            values.put(COL_USERNAME, username.trim());
            values.put(COL_PASSWORD_HASH, passwordHash);

            long result = db.insert(TABLE_USERS, null, values);
            return result != -1;

        } catch (Exception e) {
            Log.e(TAG, "Error adding user: " + e.getMessage(), e);
            return false;
        } finally {
            // FIXED - always close cursor in finally block
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // FIXED - BCrypt verification, parameterized query, try-catch-finally
    public boolean checkUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();

            // FIXED - parameterized query prevents SQL injection
            cursor = db.rawQuery(
                    "SELECT " + COL_PASSWORD_HASH + " FROM " + TABLE_USERS + " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim()}
            );

            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(0);
                // FIXED - verify password against hash instead of plain text comparison
                return BCrypt.checkpw(password, storedHash);
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking user credentials: " + e.getMessage(), e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // FIXED - new method for normalized categories table
    public long addCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be null or empty");
        }

        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_CATEGORY_NAME, categoryName.trim());
            return db.insert(TABLE_CATEGORIES, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error adding category: " + e.getMessage(), e);
            return -1;
        }
    }

    // FIXED - new method for normalized categories table
    public ArrayList<CategoryItem> getAllCategories() {
        ArrayList<CategoryItem> list = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_CATEGORY_ID + ", " + COL_CATEGORY_NAME
                    + " FROM " + TABLE_CATEGORIES + " ORDER BY " + COL_CATEGORY_NAME,
                    null
            );

            while (cursor.moveToNext()) {
                list.add(new CategoryItem(cursor.getInt(0), cursor.getString(1)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving categories: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    public int getCategoryIdByName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_CATEGORY_ID + " FROM " + TABLE_CATEGORIES + " WHERE " + COL_CATEGORY_NAME + " = ?",
                    new String[]{categoryName.trim()}
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting category ID: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return -1;
    }

    public boolean deleteCategory(int categoryId) {
        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();
            int rowsDeleted = db.delete(TABLE_CATEGORIES, COL_CATEGORY_ID + " = ?",
                    new String[]{String.valueOf(categoryId)});
            return rowsDeleted > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting category: " + e.getMessage(), e);
            return false;
        }
    }

    // FIXED - supports normalized schema with category foreign key
    public long addInventoryItem(String title, String description, int categoryId, int quantity) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }

        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(COL_TITLE, title.trim());
            values.put(COL_DESCRIPTION, description != null ? description.trim() : null);

            if (categoryId > 0) {
                values.put(COL_CATEGORY_FK, categoryId);
            }

            values.put(COL_QUANTITY, Math.max(0, quantity));

            return db.insert(TABLE_INVENTORY, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error adding inventory item: " + e.getMessage(), e);
            return -1;
        }
    }

    // Legacy method for backward compatibility
    @Deprecated
    public void addData(String title, String description) {
        addInventoryItem(title, description, -1, 0);
    }

    // FIXED - LEFT JOIN to include category names from normalized table
    public ArrayList<InventoryItem> getAllInventoryItems() {
        ArrayList<InventoryItem> list = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();

            String query = "SELECT i." + COL_INVENTORY_ID + ", i." + COL_TITLE + ", i." + COL_DESCRIPTION
                    + ", i." + COL_CATEGORY_FK + ", c." + COL_CATEGORY_NAME + ", i." + COL_QUANTITY
                    + " FROM " + TABLE_INVENTORY + " i"
                    + " LEFT JOIN " + TABLE_CATEGORIES + " c ON i." + COL_CATEGORY_FK + " = c." + COL_CATEGORY_ID
                    + " ORDER BY i." + COL_TITLE;

            cursor = db.rawQuery(query, null);

            while (cursor.moveToNext()) {
                list.add(new InventoryItem(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.isNull(3) ? null : cursor.getInt(3),
                        cursor.getString(4),
                        cursor.getInt(5)
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving inventory items: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    // Legacy method for backward compatibility
    @Deprecated
    public ArrayList<DataItem> getAllData() {
        ArrayList<DataItem> list = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_INVENTORY_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + " FROM " + TABLE_INVENTORY,
                    null
            );

            while (cursor.moveToNext()) {
                list.add(new DataItem(cursor.getInt(0), cursor.getString(1), cursor.getString(2)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving data: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    public ArrayList<InventoryItem> getInventoryByCategory(int categoryId) {
        ArrayList<InventoryItem> list = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();

            String query = "SELECT i." + COL_INVENTORY_ID + ", i." + COL_TITLE + ", i." + COL_DESCRIPTION
                    + ", i." + COL_CATEGORY_FK + ", c." + COL_CATEGORY_NAME + ", i." + COL_QUANTITY
                    + " FROM " + TABLE_INVENTORY + " i"
                    + " LEFT JOIN " + TABLE_CATEGORIES + " c ON i." + COL_CATEGORY_FK + " = c." + COL_CATEGORY_ID
                    + " WHERE i." + COL_CATEGORY_FK + " = ?"
                    + " ORDER BY i." + COL_TITLE;

            // FIXED - parameterized query
            cursor = db.rawQuery(query, new String[]{String.valueOf(categoryId)});

            while (cursor.moveToNext()) {
                list.add(new InventoryItem(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.isNull(3) ? null : cursor.getInt(3),
                        cursor.getString(4),
                        cursor.getInt(5)
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving inventory by category: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    public boolean updateInventoryItem(int id, String title, String description, int categoryId, int quantity) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }

        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(COL_TITLE, title.trim());
            values.put(COL_DESCRIPTION, description != null ? description.trim() : null);

            if (categoryId > 0) {
                values.put(COL_CATEGORY_FK, categoryId);
            } else {
                values.putNull(COL_CATEGORY_FK);
            }

            values.put(COL_QUANTITY, Math.max(0, quantity));

            // FIXED - parameterized update
            int rowsUpdated = db.update(TABLE_INVENTORY, values, COL_INVENTORY_ID + " = ?",
                    new String[]{String.valueOf(id)});

            return rowsUpdated > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating inventory item: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean updateQuantity(int id, int quantity) {
        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(COL_QUANTITY, Math.max(0, quantity));

            int rowsUpdated = db.update(TABLE_INVENTORY, values, COL_INVENTORY_ID + " = ?",
                    new String[]{String.valueOf(id)});

            return rowsUpdated > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating quantity: " + e.getMessage(), e);
            return false;
        }
    }

    // FIXED - parameterized delete
    public boolean deleteInventoryItem(int id) {
        SQLiteDatabase db = null;

        try {
            db = this.getWritableDatabase();
            int rowsDeleted = db.delete(TABLE_INVENTORY, COL_INVENTORY_ID + " = ?",
                    new String[]{String.valueOf(id)});
            return rowsDeleted > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting inventory item: " + e.getMessage(), e);
            return false;
        }
    }

    // Legacy method for backward compatibility
    @Deprecated
    public void deleteData(int id) {
        deleteInventoryItem(id);
    }

    public int getInventoryCount() {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_INVENTORY, null);

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting inventory count: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }

    public boolean usernameExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            // FIXED - parameterized query
            cursor = db.rawQuery("SELECT 1 FROM " + TABLE_USERS + " WHERE " + COL_USERNAME + " = ?",
                    new String[]{username.trim()});
            return cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking username: " + e.getMessage(), e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
