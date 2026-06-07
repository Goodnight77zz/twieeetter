import 'package:flutter/material.dart';
import 'package:provider/provider.dart'; // 引入
import 'app_lang.dart'; // 引入
import 'package:shared_preferences/shared_preferences.dart';
import 'register_page.dart';
import 'api_config.dart';
import 'api_helpers.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _userCtrl = TextEditingController();
  final TextEditingController _passCtrl = TextEditingController();
  bool _isLoading = false;

  Future<void> _doLogin() async {
    // 获取语言工具
    final lang = context.read<LangProvider>();

    if (_userCtrl.text.isEmpty || _passCtrl.text.isEmpty) return;
    setState(() => _isLoading = true);
    try {
      final res = await createSessionDio().post(
        '$baseUrl/auth/login',
        data: {"username": _userCtrl.text, "password": _passCtrl.text},
      );
      if (res.data['message'] == 'success') {
        final prefs = await SharedPreferences.getInstance();
        // 假设后端返回了 userId (你在 AuthController 里返回了吗？)
        // 如果你的 AuthController 返回的是 {"message": "success", "userId": 123}
        await prefs.setInt('userId', res.data['userId']);
        if (mounted)
          Navigator.pushNamedAndRemoveUntil(context, '/main', (r) => false);
      } else {
        // 使用多语言提示
        if (mounted)
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(lang.t('login_failed'))));
      }
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("${lang.t('network_error')}: $e")),
        );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    const mainBlue = Color(0xFF00A1D6);
    // 🔥 核心：获取当前语言状态，并监听变化
    final lang = context.watch<LangProvider>();

    return Scaffold(
      backgroundColor: Colors.white,
      // 🔥 增加一个右上角的切换按钮
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          TextButton.icon(
            onPressed: () {
              // 一键切换语言
              context.read<LangProvider>().switchLang();
            },
            icon: const Icon(Icons.language, color: mainBlue),
            label: Text(
              lang.currentLang == 'zh' ? 'English' : '中文',
              style: const TextStyle(
                color: mainBlue,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(width: 10),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 30),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.school, size: 80, color: mainBlue),
              const SizedBox(height: 20),
              // 使用 lang.t('key')
              Text(
                lang.t('app_name'),
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: mainBlue,
                ),
              ),
              const SizedBox(height: 50),

              TextField(
                controller: _userCtrl,
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.person_outline, color: mainBlue),
                  hintText: lang.t('account_hint'), // 替换
                  filled: true,
                  fillColor: const Color(0xFFF6F7F9),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(50),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              TextField(
                controller: _passCtrl,
                obscureText: true,
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.lock_outline, color: mainBlue),
                  hintText: lang.t('password_hint'), // 替换
                  filled: true,
                  fillColor: const Color(0xFFF6F7F9),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(50),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 40),

              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _doLogin,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: mainBlue,
                    foregroundColor: Colors.white,
                    elevation: 2,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(50),
                    ),
                  ),
                  child: _isLoading
                      ? const CircularProgressIndicator(color: Colors.white)
                      : Text(
                          lang.t('login_btn'),
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ), // 替换
                ),
              ),
              const SizedBox(height: 20),
              const SizedBox(height: 20),
              TextButton(
                onPressed: () {
                  // 🔥 修改这里：跳转到注册页
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const RegisterPage(),
                    ),
                  );
                },
                child: Text(
                  lang.t('go_register'),
                  style: const TextStyle(color: Colors.grey),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
