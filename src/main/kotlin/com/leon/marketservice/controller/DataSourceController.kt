package com.leon.marketservice.controller

import com.leon.marketservice.model.DataSourceSwitchRequest
import com.leon.marketservice.service.MarketDataService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for managing data source switching
 * Provides endpoints for switching between Alpha Vantage and AllTick data sources
 * 
 * This controller allows clients to dynamically switch data sources either globally
 * or for specific stocks, providing flexibility in data source selection
 */
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin
class DataSourceController(private val marketDataService: MarketDataService)
{
    private val logger = LoggerFactory.getLogger(DataSourceController::class.java)

    /**
     * Switch the global default data source for all stocks
     * Changes the default data source that will be used for new subscriptions
     * 
     * @param request The data source switch request
     * @return ResponseEntity with success/failure message
     */
    @PostMapping("/global")
    fun switchGlobalDataSource(@Valid @RequestBody request: DataSourceSwitchRequest): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Switching global data source to: ${request.dataSource}")
        
        return try 
        {
            marketDataService.switchGlobalDataSource(request.dataSource)
            logger.info("Successfully switched global data source to: ${request.dataSource}")
            ResponseEntity.ok(mapOf("success" to true, "message" to "Global data source switched to ${request.dataSource}", "dataSource" to request.dataSource))
        } 
        catch (e: Exception) 
        {
            logger.error("Failed to switch global data source to: ${request.dataSource}", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("success" to false, "message" to "Failed to switch data source: ${e.message}"))
        }
    }

    /**
     * Switch data source for specific stocks
     * Changes the data source for the specified RICs only
     * 
     * @param request The data source switch request with RICs
     * @return ResponseEntity with success/failure message
     */
    @PostMapping("/stock")
    fun switchStockDataSource(@Valid @RequestBody request: DataSourceSwitchRequest): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Switching data source for RICs: ${request.rics} to: ${request.dataSource}")
        return try 
        {
            if (request.rics.isNullOrEmpty())
                return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "RICs list cannot be empty for stock-specific data source switching"))
            
            marketDataService.switchStockDataSource(request.rics, request.dataSource)
            logger.info("Successfully switched data source for RICs: ${request.rics} to: ${request.dataSource}")
            ResponseEntity.ok(mapOf("success" to true, "message" to "Data source switched to ${request.dataSource} for specified RICs",
                "dataSource" to request.dataSource, "rics" to request.rics ))
        } 
        catch (e: Exception) 
        {
            logger.error("Failed to switch data source for RICs: ${request.rics}", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("success" to false, "message" to "Failed to switch data source: ${e.message}"))
        }
    }

    /**
     * Get current data source status
     * Returns information about the current global data source and stock-specific overrides
     * 
     * @return ResponseEntity containing data source status information
     */
    @GetMapping("/status")
    fun getDataSourceStatus(): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Retrieving data source status")
        
        return try 
        {
            val status = marketDataService.getDataSourceStatus()
            logger.info("Successfully retrieved data source status")
            ResponseEntity.ok(status)
        } 
        catch (e: Exception) 
        {
            logger.error("Failed to retrieve data source status", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve data source status: ${e.message}"))
        }
    }
}