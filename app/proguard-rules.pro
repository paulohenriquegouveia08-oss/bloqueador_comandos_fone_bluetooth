# O app não expõe API nem usa reflexão própria; as regras padrão do
# Android bastam. Estas duas existem só para o R8 não remover pontos de
# entrada que o SISTEMA instancia por nome — ele não vê essas chamadas.
-keep class com.pk.bluetoothmediaguard.service.** { *; }
-keep class com.pk.bluetoothmediaguard.GuardApplication { *; }
