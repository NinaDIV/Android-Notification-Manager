package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.data.datasource.AppDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.LocalAuthDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.LocalUserDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteAuthDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteUserDataSource
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.network.RetryPolicy
import com.dynamictecnologies.notificationmanager.data.repository.AppRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.data.repository.PreferencesRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.repository.UserProfileRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.storage.SecureSessionStorage
import com.dynamictecnologies.notificationmanager.data.storage.SessionStorage
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
import com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterUserWithUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetInstalledAppsUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.SaveSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.GetUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RefreshUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RegisterUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.ValidateUsernameUseCase
import com.dynamictecnologies.notificationmanager.presentation.auth.GoogleSignInHelper
import com.dynamictecnologies.notificationmanager.util.logging.AndroidLogger
import com.dynamictecnologies.notificationmanager.util.logging.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt principal que provee todas las dependencias del proyecto.
 * 
 * Reemplaza los módulos manuales: AuthModule, AppModule, BluetoothMqttModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppHiltModule {
    
    // ========================================
    // FIREBASE
    // ========================================
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()
    
    // ========================================
    // STORAGE & VALIDATORS
    // ========================================
    
    @Provides
    @Singleton
    fun provideSessionStorage(@ApplicationContext context: Context): SessionStorage =
        SecureSessionStorage(context)
    
    @Provides
    fun provideAuthValidator(): AuthValidator = AuthValidator()
    
    @Provides
    fun provideUsernameValidator(): UsernameValidator = UsernameValidator()
    
    @Provides
    fun provideAuthErrorMapper(): AuthErrorMapper = AuthErrorMapper()
    
    @Provides
    fun provideLogger(): Logger = AndroidLogger()
    
    @Provides
    fun provideGoogleSignInHelper(@ApplicationContext context: Context): GoogleSignInHelper =
        GoogleSignInHelper(context)
    
    // ========================================
    // DATA SOURCES
    // ========================================
    
    @Provides
    fun provideRemoteAuthDataSource(firebaseAuth: FirebaseAuth): RemoteAuthDataSource =
        RemoteAuthDataSource(firebaseAuth)
    
    @Provides
    fun provideLocalAuthDataSource(sessionStorage: SessionStorage): LocalAuthDataSource =
        LocalAuthDataSource(sessionStorage)
    
    @Provides
    fun provideRemoteUserDataSource(firebaseDatabase: FirebaseDatabase): RemoteUserDataSource =
        RemoteUserDataSource(firebaseDatabase)
    
    @Provides
    fun provideLocalUserDataSource(): LocalUserDataSource = LocalUserDataSource()
    
    @Provides
    fun provideAppDataSource(@ApplicationContext context: Context): AppDataSource =
        AppDataSource(context.packageManager)
    
    // ========================================
    // DATABASE
    // ========================================
    
    @Provides
    @Singleton
    fun provideNotificationDatabase(@ApplicationContext context: Context): NotificationDatabase =
        NotificationDatabase.getDatabase(context)
    
    @Provides
    @Singleton
    fun provideNotificationDao(database: NotificationDatabase): NotificationDao =
        database.notificationDao()
    
    // ========================================
    // REPOSITORIES
    // ========================================
    
    @Provides
    @Singleton
    fun provideAuthRepository(
        remoteDataSource: RemoteAuthDataSource,
        localDataSource: LocalAuthDataSource,
        validator: AuthValidator,
        errorMapper: AuthErrorMapper
    ): AuthRepository = AuthRepositoryImpl(
        remoteDataSource = remoteDataSource,
        localDataSource = localDataSource,
        validator = validator,
        errorMapper = errorMapper
    )
    
    @Provides
    @Singleton
    fun provideUserProfileRepository(
        remoteDataSource: RemoteUserDataSource,
        localDataSource: LocalUserDataSource,
        usernameValidator: UsernameValidator,
        firebaseAuth: FirebaseAuth,
        authRepository: AuthRepository
    ): UserProfileRepository = UserProfileRepositoryImpl(
        remoteDataSource = remoteDataSource,
        localDataSource = localDataSource,
        usernameValidator = usernameValidator,
        firebaseAuth = firebaseAuth,
        authRepository = authRepository
    )
    
    @Provides
    @Singleton
    fun provideNotificationRepository(
        notificationDao: NotificationDao,
        @ApplicationContext context: Context
    ): NotificationRepository = NotificationRepository(
        notificationDao = notificationDao,
        context = context
    )
    
    @Provides
    fun provideAppRepository(appDataSource: AppDataSource): AppRepository =
        AppRepositoryImpl(appDataSource = appDataSource)
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    
    @Provides
    fun providePreferencesRepository(sharedPreferences: SharedPreferences): PreferencesRepository =
        PreferencesRepositoryImpl(sharedPreferences = sharedPreferences)
    
    // ========================================
    // USE CASES - AUTH
    // ========================================
    
    @Provides
    fun provideSignInWithEmailUseCase(authRepository: AuthRepository) =
        SignInWithEmailUseCase(authRepository)
    
    @Provides
    fun provideRegisterWithEmailUseCase(authRepository: AuthRepository) =
        RegisterWithEmailUseCase(authRepository)
    
    @Provides
    fun provideRegisterUserWithUsernameUseCase(
        authRepository: AuthRepository,
        userProfileRepository: UserProfileRepository
    ) = RegisterUserWithUsernameUseCase(authRepository, userProfileRepository)
    
    @Provides
    fun provideSignInWithGoogleUseCase(authRepository: AuthRepository) =
        SignInWithGoogleUseCase(authRepository)
    
    @Provides
    fun provideSignOutUseCase(
        authRepository: AuthRepository,
        userProfileRepository: UserProfileRepository
    ) = SignOutUseCase(authRepository, userProfileRepository)
    
    @Provides
    fun provideGetCurrentUserUseCase(authRepository: AuthRepository) =
        GetCurrentUserUseCase(authRepository)
    
    @Provides
    fun provideValidateSessionUseCase(authRepository: AuthRepository) =
        ValidateSessionUseCase(authRepository)
    
    // ========================================
    // USE CASES - USER PROFILE
    // ========================================
    
    @Provides
    fun provideGetUserProfileUseCase(userProfileRepository: UserProfileRepository) =
        GetUserProfileUseCase(userProfileRepository)
    
    @Provides
    fun provideRegisterUsernameUseCase(userProfileRepository: UserProfileRepository) =
        RegisterUsernameUseCase(userProfileRepository)
    
    @Provides
    fun provideRefreshUserProfileUseCase(userProfileRepository: UserProfileRepository) =
        RefreshUserProfileUseCase(userProfileRepository)
    
    // ========================================
    // USE CASES - APP
    // ========================================
    
    @Provides
    fun provideGetInstalledAppsUseCase(appRepository: AppRepository) =
        GetInstalledAppsUseCase(appRepository)
    
    @Provides
    fun provideSaveSelectedAppUseCase(preferencesRepository: PreferencesRepository) =
        SaveSelectedAppUseCase(preferencesRepository)
    
    @Provides
    fun provideGetSelectedAppUseCase(
        preferencesRepository: PreferencesRepository,
        appRepository: AppRepository
    ) = GetSelectedAppUseCase(preferencesRepository, appRepository)
}
