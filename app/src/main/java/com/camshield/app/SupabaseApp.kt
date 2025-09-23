package com.camshield.app

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage


class App : Application() {


    companion object {
        lateinit var supabase: SupabaseClient
    }


    override fun onCreate() {
        super.onCreate()

        supabase = createSupabaseClient(
            supabaseUrl = "https://tewchlxrvfuzusdnhynk.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRld2NobHhydmZ1enVzZG5oeW5rIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyNDIwMDgsImV4cCI6MjA3MzgxODAwOH0.35QdJV3WnOFzKmVPX_R4iOM0VXAbRnXJyuIZOXNamRU"
        ) {
            install(Storage)
        }
    }
}

// Add this test function to verify everything works:
suspend fun testSupabaseSetup() {
    try {
        println("Testing Supabase connection...")

        // Test basic connection
        val buckets = App.supabase.storage.retrieveBuckets()
        println("✓ Connection successful")
        println("Available buckets: ${buckets.map { it.id }}")

        // Check specific bucket
        val contactBucket = buckets.find { it.id == "contact-images" }
        if (contactBucket != null) {
            println("✓ contact-images bucket found (public: ${contactBucket.public})")
        } else {
            println("✗ contact-images bucket NOT found - create it in Supabase dashboard")
        }

        // Test auth
        val user = App.supabase.auth.currentUserOrNull()
        println("Current user: ${user?.id ?: "Not authenticated"}")

    } catch (e: Exception) {
        println("✗ Supabase test failed: ${e.message}")
        e.printStackTrace()
    }
}