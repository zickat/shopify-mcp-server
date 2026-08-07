package com.zickat.shopifymcpserver.shared_kernel

import java.util.Date
import kotlin.time.Instant
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

@Configuration
class MongoConfiguration {

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions =
        MongoCustomConversions(listOf(InstantToDateConverter, DateToInstantConverter))
}

@WritingConverter
object InstantToDateConverter : Converter<Instant, Date> {
    override fun convert(source: Instant): Date = Date(source.toEpochMilliseconds())
}

@ReadingConverter
object DateToInstantConverter : Converter<Date, Instant> {
    override fun convert(source: Date): Instant = Instant.fromEpochMilliseconds(source.time)
}
