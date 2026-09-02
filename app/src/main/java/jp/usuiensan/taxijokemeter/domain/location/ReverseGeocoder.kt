package jp.usuiensan.taxijokemeter.domain.location

import jp.usuiensan.taxijokemeter.domain.model.RecordedPlace

interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): RecordedPlace
}
