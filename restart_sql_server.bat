@echo off
:: =============================================================================
:: Script khởi động lại SQL Server để áp dụng cấu hình TCP/IP 1433 & Mixed Mode
:: Hãy chạy file này bằng chuột phải -> "Run as administrator" (Chạy với quyền Quản trị)
:: =============================================================================
echo ============================================================
echo   KHOI DONG LAI DICH VU SQL SERVER (MSSQLSERVER)
echo ============================================================
echo Dang dung dich vu MSSQLSERVER...
net stop MSSQLSERVER
echo.
echo Dang khoi dong lai dich vu MSSQLSERVER...
net start MSSQLSERVER
echo.
echo ============================================================
echo   HOAN TAT! CONG 1433 VA USER 'sa' DA SAN SANG PHUC VU JDBC!
echo ============================================================
pause
