package com.stefanodefaria.fechaduraeletronica


interface Toaster {
    fun toast(message: String)
    fun toast(stringId: Int)
    fun getString(stringId: Int) : String
}
