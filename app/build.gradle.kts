import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Credenciais da chave de release, fora do repositório.
 *
 * Sem o arquivo, o build de release sai SEM assinatura em vez de falhar:
 * quem clona o projeto consegue compilar e testar; só quem tem a chave
 * consegue produzir o APK que atualiza o app instalado.
 */
val chaveDeAssinatura = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "com.pk.bluetoothmediaguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pk.bluetoothmediaguard"
        // 26 e o piso real: abaixo disso o roteamento de botao de midia
        // usa o broadcast ACTION_MEDIA_BUTTON, que e outro mecanismo — e
        // suportar os dois dobraria a superficie de teste sem publico.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val caminho = chaveDeAssinatura.getProperty("storeFile")
            if (caminho != null) {
                storeFile = file(caminho)
                storePassword = chaveDeAssinatura.getProperty("storePassword")
                keyAlias = chaveDeAssinatura.getProperty("keyAlias")
                keyPassword = chaveDeAssinatura.getProperty("keyPassword")

                // v2 e v3 ligados explicitamente. O v3 é o esquema que o
                // Android moderno prefere e o que permite trocar a chave
                // no futuro sem quebrar as atualizações; sem ele, o APK
                // é verificado por um caminho mais antigo.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    /**
     * Dois sabores, por causa do Play Protect do Brasil.
     *
     * `completo` é o app como projetado. `leve` remove o serviço de
     * notificações — a única coisa que o Play Protect sinaliza — para
     * conseguir ser instalado por download. A diferença de capacidade
     * está no README e na própria tela.
     */
    flavorDimensions += "distribuicao"
    productFlavors {
        create("completo") {
            dimension = "distribuicao"
            buildConfigField("boolean", "TEM_ACESSO_A_SESSOES", "true")
        }
        create("leve") {
            dimension = "distribuicao"
            applicationIdSuffix = ".leve"
            versionNameSuffix = "-leve"
            buildConfigField("boolean", "TEM_ACESSO_A_SESSOES", "false")
        }
    }

    buildTypes {
        release {
            // Assinado com chave própria, e não com a de depuração.
            //
            // Um APK de depuração é marcado como `debuggable` e assinado
            // com a chave genérica do SDK — as duas coisas fazem o Play
            // Protect tratá-lo como desconhecido e avisar na instalação.
            signingConfig = if (chaveDeAssinatura.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }

            // Encolhe o APK e remove código não usado. Menos superfície
            // para um scanner analisar, e download menor.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Warnings viram erro: a especificacao pede "sem warnings
        // criticos", e o unico jeito de isso valer e o build recusar.
        allWarningsAsErrors = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
