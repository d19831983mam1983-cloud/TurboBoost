#!/usr/bin/env python3
"""Генератор соли и хеша пароля админа для AdminAuth.kt.
Запуск: python3 tools/make_admin_hash.py
Вставь выведенные SALT и HASH в AdminAuth.kt (SALT1/HASH1 для ПСИХОЗ, SALT2/HASH2 для Owner)."""
import getpass, hashlib, secrets

ITER = 120000
pw = getpass.getpass("Новый пароль админа: ")
if len(pw) < 10:
    raise SystemExit("Пароль слишком короткий: нужно минимум 10 символов (лучше 14 и больше).")
salt = secrets.token_bytes(16)
h = hashlib.pbkdf2_hmac("sha256", pw.encode("utf-8"), salt, ITER, dklen=32)
print("SALT =", salt.hex())
print("HASH =", h.hex())
