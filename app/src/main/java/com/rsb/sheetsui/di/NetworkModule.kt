package com.rsb.sheetsui.di

import com.rsb.sheetsui.BuildConfig
import com.rsb.sheetsui.data.remote.api.GoogleDriveService
import com.rsb.sheetsui.data.remote.api.GoogleFormsService
import com.rsb.sheetsui.data.remote.api.GoogleSheetService
import com.rsb.sheetsui.data.remote.interceptor.AuthInterceptor
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    @Provides
    @Singleton
    @SheetsRetrofit
    fun provideSheetsRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    @DriveRetrofit
    fun provideDriveRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideGoogleSheetService(@SheetsRetrofit retrofit: Retrofit): GoogleSheetService =
        retrofit.create(GoogleSheetService::class.java)

    @Provides
    @Singleton
    fun provideGoogleDriveService(@DriveRetrofit retrofit: Retrofit): GoogleDriveService =
        retrofit.create(GoogleDriveService::class.java)

    @Provides
    @Singleton
    @FormsRetrofit
    fun provideFormsRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://forms.googleapis.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideGoogleFormsService(@FormsRetrofit retrofit: Retrofit): GoogleFormsService =
        retrofit.create(GoogleFormsService::class.java)
}
