package com.leon.marketservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MarketServiceApplication

fun main(args: Array<String>) 
{
    runApplication<MarketServiceApplication>(*args)
}