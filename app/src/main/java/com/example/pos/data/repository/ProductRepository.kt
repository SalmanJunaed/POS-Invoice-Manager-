package com.example.pos.data.repository

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.ProductDao
import com.example.pos.data.db.ProductEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ProductRepository {
    val activeProducts: Flow<List<ProductEntity>>
    val allProducts: Flow<List<ProductEntity>>
    suspend fun getProductById(itemId: String): ProductEntity?
    suspend fun saveProduct(
        itemId: String?,
        itemName: String,
        itemType: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ): PosResult<ProductEntity>
    suspend fun deleteProduct(itemId: String): PosResult<Unit>
    suspend fun updateSortOrder(itemId: String, newOrder: Int): PosResult<Unit>
}

class ProductRepositoryImpl(private val productDao: ProductDao) : ProductRepository {

    override val activeProducts: Flow<List<ProductEntity>> = productDao.getAllActiveProductsFlow()
    override val allProducts: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()

    override suspend fun getProductById(itemId: String): ProductEntity? = productDao.getProductById(itemId)

    override suspend fun saveProduct(
        itemId: String?,
        itemName: String,
        itemType: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ): PosResult<ProductEntity> {
        val id = itemId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val entity = ProductEntity(
            itemId = id,
            itemName = itemName.trim(),
            itemType = itemType,
            unit = unit.trim(),
            defaultUnitPricePaisa = unitPricePaisa,
            sortOrder = sortOrder,
            isActive = true
        )
        productDao.insertProduct(entity)
        return PosResult.Success(entity)
    }

    override suspend fun deleteProduct(itemId: String): PosResult<Unit> {
        productDao.deleteProductById(itemId)
        return PosResult.Success(Unit)
    }

    override suspend fun updateSortOrder(itemId: String, newOrder: Int): PosResult<Unit> {
        productDao.updateSortOrder(itemId, newOrder)
        return PosResult.Success(Unit)
    }
}
