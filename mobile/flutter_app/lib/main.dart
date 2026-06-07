import 'package:flutter/material.dart';
import 'login_page.dart';
import 'main_screen.dart';
import 'package:provider/provider.dart'; // 引入 provider
import 'app_lang.dart'; // 引入刚才写的语言包
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (context) => LangProvider(),
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  // 默认显示个空白页或加载页，等待检查结果
  Widget _defaultHome = const Scaffold(
    body: Center(child: CircularProgressIndicator()),
  );

  @override
  void initState() {
    super.initState();
    _checkLoginStatus();
  }

  // 🔥 检查登录状态
  Future<void> _checkLoginStatus() async {
    final prefs = await SharedPreferences.getInstance();
    final int? userId = prefs.getInt('userId');

    setState(() {
      if (userId != null && userId > 0) {
        // 有ID，直接去首页
        _defaultHome = const MainScreen();
      } else {
        // 没ID，去登录页
        _defaultHome = const LoginPage();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Twieeetter',
      debugShowCheckedModeBanner: false,
      // ... theme 配置保持不变 ...
      theme: ThemeData(
        // 把你之前的 Theme 代码放这里
        primaryColor: const Color(0xFF00A1D6),
        scaffoldBackgroundColor: const Color(0xFFF0F2F5),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00A1D6),
          primary: const Color(0xFF00A1D6),
        ),
        useMaterial3: true,
      ),

      // 🔥 关键修改：不再使用 initialRoute，而是直接指定 home
      home: _defaultHome,

      // 路由表保留，方便跳转
      routes: {
        '/login': (context) => const LoginPage(),
        '/main': (context) => const MainScreen(),
      },
    );
  }
}
