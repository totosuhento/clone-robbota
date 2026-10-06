package com.robotta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.robotta.data.entities.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE id = 1")
    fun observeProfile(): Flow<Account?>

    @Query("SELECT * FROM accounts WHERE id = 1")
    suspend fun getProfile(): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(account: Account)
}
