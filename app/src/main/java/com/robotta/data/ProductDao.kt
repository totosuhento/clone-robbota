package com.robotta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.robotta.data.entities.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Int): Product?

    @Query("SELECT * FROM products WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Insert
    suspend fun insertAll(products: List<Product>): List<Long>

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("UPDATE products SET status = :status, errorMessage = :error, postedAt = :postedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, error: String, postedAt: Long?)

    @Query("UPDATE products SET status = 'pending', errorMessage = '' WHERE status IN ('failed', 'skipped')")
    suspend fun resetFailedAndSkipped(): Int
}
