package com.example.aiassistant;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile LogDao _logDao;

  private volatile RuleDao _ruleDao;

  private volatile UserPatternDao _userPatternDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `LogEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `state` TEXT NOT NULL, `actionType` TEXT NOT NULL, `actionDetail` TEXT NOT NULL, `success` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `RuleEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `keyword` TEXT NOT NULL, `actionType` TEXT NOT NULL, `enabled` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `UserPatternEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `state` TEXT NOT NULL, `packageName` TEXT NOT NULL, `actionText` TEXT NOT NULL, `actionType` TEXT NOT NULL, `count` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_UserPatternEntity_state_actionText` ON `UserPatternEntity` (`state`, `actionText`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b1f6a18168a307cfe18ed875ceee7b90')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `LogEntity`");
        db.execSQL("DROP TABLE IF EXISTS `RuleEntity`");
        db.execSQL("DROP TABLE IF EXISTS `UserPatternEntity`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsLogEntity = new HashMap<String, TableInfo.Column>(7);
        _columnsLogEntity.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("actionType", new TableInfo.Column("actionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("actionDetail", new TableInfo.Column("actionDetail", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLogEntity.put("success", new TableInfo.Column("success", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLogEntity = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLogEntity = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoLogEntity = new TableInfo("LogEntity", _columnsLogEntity, _foreignKeysLogEntity, _indicesLogEntity);
        final TableInfo _existingLogEntity = TableInfo.read(db, "LogEntity");
        if (!_infoLogEntity.equals(_existingLogEntity)) {
          return new RoomOpenHelper.ValidationResult(false, "LogEntity(com.example.aiassistant.LogEntity).\n"
                  + " Expected:\n" + _infoLogEntity + "\n"
                  + " Found:\n" + _existingLogEntity);
        }
        final HashMap<String, TableInfo.Column> _columnsRuleEntity = new HashMap<String, TableInfo.Column>(4);
        _columnsRuleEntity.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRuleEntity.put("keyword", new TableInfo.Column("keyword", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRuleEntity.put("actionType", new TableInfo.Column("actionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRuleEntity.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRuleEntity = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRuleEntity = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRuleEntity = new TableInfo("RuleEntity", _columnsRuleEntity, _foreignKeysRuleEntity, _indicesRuleEntity);
        final TableInfo _existingRuleEntity = TableInfo.read(db, "RuleEntity");
        if (!_infoRuleEntity.equals(_existingRuleEntity)) {
          return new RoomOpenHelper.ValidationResult(false, "RuleEntity(com.example.aiassistant.RuleEntity).\n"
                  + " Expected:\n" + _infoRuleEntity + "\n"
                  + " Found:\n" + _existingRuleEntity);
        }
        final HashMap<String, TableInfo.Column> _columnsUserPatternEntity = new HashMap<String, TableInfo.Column>(7);
        _columnsUserPatternEntity.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("actionText", new TableInfo.Column("actionText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("actionType", new TableInfo.Column("actionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("count", new TableInfo.Column("count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserPatternEntity.put("lastSeen", new TableInfo.Column("lastSeen", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserPatternEntity = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserPatternEntity = new HashSet<TableInfo.Index>(1);
        _indicesUserPatternEntity.add(new TableInfo.Index("index_UserPatternEntity_state_actionText", true, Arrays.asList("state", "actionText"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoUserPatternEntity = new TableInfo("UserPatternEntity", _columnsUserPatternEntity, _foreignKeysUserPatternEntity, _indicesUserPatternEntity);
        final TableInfo _existingUserPatternEntity = TableInfo.read(db, "UserPatternEntity");
        if (!_infoUserPatternEntity.equals(_existingUserPatternEntity)) {
          return new RoomOpenHelper.ValidationResult(false, "UserPatternEntity(com.example.aiassistant.UserPatternEntity).\n"
                  + " Expected:\n" + _infoUserPatternEntity + "\n"
                  + " Found:\n" + _existingUserPatternEntity);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b1f6a18168a307cfe18ed875ceee7b90", "74daee32a0f9a2529db3f4217b2b0ecc");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "LogEntity","RuleEntity","UserPatternEntity");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `LogEntity`");
      _db.execSQL("DELETE FROM `RuleEntity`");
      _db.execSQL("DELETE FROM `UserPatternEntity`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(LogDao.class, LogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RuleDao.class, RuleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserPatternDao.class, UserPatternDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public LogDao logDao() {
    if (_logDao != null) {
      return _logDao;
    } else {
      synchronized(this) {
        if(_logDao == null) {
          _logDao = new LogDao_Impl(this);
        }
        return _logDao;
      }
    }
  }

  @Override
  public RuleDao ruleDao() {
    if (_ruleDao != null) {
      return _ruleDao;
    } else {
      synchronized(this) {
        if(_ruleDao == null) {
          _ruleDao = new RuleDao_Impl(this);
        }
        return _ruleDao;
      }
    }
  }

  @Override
  public UserPatternDao userPatternDao() {
    if (_userPatternDao != null) {
      return _userPatternDao;
    } else {
      synchronized(this) {
        if(_userPatternDao == null) {
          _userPatternDao = new UserPatternDao_Impl(this);
        }
        return _userPatternDao;
      }
    }
  }
}
