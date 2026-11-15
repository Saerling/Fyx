# Seguridad — Fyx

| # | Punto | Estado |
|---|---|---|
| 1 | Ocultar API keys | ✅ Sin claves en el código; la cuenta de Firefox usa OAuth de Mozilla. |
| 2 | Secretos fuera de Git | ⚠️ `android/keystore/kaze-release.p12` está en el repo, pero **cifrado**: su contraseña solo vive en el secreto `KAZE_KEYSTORE_PASSWORD`. Mientras la contraseña sea larga y aleatoria, el archivo no sirve a nadie. |
| 3 | Key pública para DB | N/A — datos locales (SQLite) y Firefox Sync con cifrado de extremo a extremo. |
| 4 | Row-Level Security | N/A. Cada perfil filtra por `profile_id`. |
| 5 | Encriptar datos sensibles | ✅ Contraseñas guardadas con AES-GCM (Android Keystore); sesión de Firefox con `security-crypto`. |
| 6 | Forzar autenticación | ✅ Firefox Sync exige iniciar sesión en Mozilla. |
| 7 | Restringir acceso a registros | ✅ Solo se exportan la pantalla principal, los enlaces y el servicio Custom Tabs (obligatorio para un navegador). |
| 8 | Bloquear manipulación de campos | ✅ Los Intents se validan antes de abrir URLs. |
| 9 | Session cookies | ✅ Las gestiona GeckoView, aisladas por perfil y con modo privado. |
| 10 | Hashear contraseñas | N/A — Fyx no tiene cuentas propias. |
| 11 | Rate limiting | N/A — cliente. |
| 12 | Protección contra bots | ✅ Protección contra rastreo de Firefox (ETP) activa. |
| 13 | Queries parametrizadas | ✅ Todo SQL usa `?`. |
| 14 | Validar inputs | ✅ La barra de direcciones distingue URL/búsqueda y bloquea esquemas peligrosos. |
| 15 | Sanitizar contenido | ✅ Las webs corren en el sandbox multiproceso de GeckoView. |
| 16 | Restringir archivos | ✅ Descargas a la carpeta pública con confirmación; sin acceso a archivos privados. |
| 17 | Devolver solo lo necesario | ✅ `allowBackup=false`: nada sale en copias de Android. |
| 18 | Security headers | N/A — navegador (respeta los de cada web). |
| 19 | Forzar HTTPS | ✅ HTTPS-First siempre y solo-HTTPS en privado; actualizaciones solo desde GitHub por HTTPS y Android verifica la firma. |
| 20 | Escanear dependencias | ✅ Dependabot (Gradle, npm y Actions). |
