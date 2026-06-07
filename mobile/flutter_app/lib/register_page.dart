import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'app_lang.dart';
import 'api_config.dart';
import 'api_helpers.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  // 你的服务器地址
  final Color mainBlue = const Color(0xFF00A1D6);

  final TextEditingController _userCtrl = TextEditingController();
  final TextEditingController _emailCtrl = TextEditingController();
  final TextEditingController _passCtrl = TextEditingController();
  final TextEditingController _confirmPassCtrl = TextEditingController();

  bool _isLoading = false;

  Future<void> _doRegister() async {
    final lang = context.read<LangProvider>();

    // 1. 基础校验
    if (_userCtrl.text.isEmpty ||
        _emailCtrl.text.isEmpty ||
        _passCtrl.text.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(lang.t('please_input'))));
      return;
    }

    // 2. 密码一致性校验
    if (_passCtrl.text != _confirmPassCtrl.text) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(lang.t('password_mismatch'))));
      return;
    }

    setState(() => _isLoading = true);

    try {
      // 3. 发送请求
      // 注意：你的后端接收的是 User 对象，字段名要和 Entity 对应
      var res = await createSessionDio().post(
        '$baseUrl/auth/register',
        data: {
          "username": _userCtrl.text,
          "email": _emailCtrl.text,
          "password": _passCtrl.text,
        },
      );

      // 4. 处理结果 (后端返回的是 String，如 "注册成功" 或 错误信息)
      String result = res.data.toString();

      if (result.contains("成功") || result.contains("success")) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(lang.t('register_success')),
              backgroundColor: Colors.green,
            ),
          );
          // 注册成功后返回登录页
          Navigator.pop(context);
        }
      } else {
        if (mounted)
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(result)));
      }
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text("Error: $e")));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          // 防止键盘遮挡
          padding: const EdgeInsets.symmetric(horizontal: 30),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(height: 20),
              Icon(Icons.person_add_alt_1, size: 80, color: mainBlue),
              const SizedBox(height: 20),
              Text(
                lang.t('register_title'),
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: mainBlue,
                ),
              ),
              const SizedBox(height: 40),

              // 用户名
              _buildInput(
                _userCtrl,
                Icons.person_outline,
                lang.t('account_hint'),
              ),
              const SizedBox(height: 20),

              // 邮箱
              _buildInput(
                _emailCtrl,
                Icons.email_outlined,
                lang.t('email_hint'),
              ),
              const SizedBox(height: 20),

              // 密码
              _buildInput(
                _passCtrl,
                Icons.lock_outline,
                lang.t('password_hint'),
                isPwd: true,
              ),
              const SizedBox(height: 20),

              // 确认密码
              _buildInput(
                _confirmPassCtrl,
                Icons.verified_user,
                lang.t('confirm_password_hint'),
                isPwd: true,
              ),

              const SizedBox(height: 40),

              // 注册按钮
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _doRegister,
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
                          lang.t('register_btn'),
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),
              const SizedBox(height: 20),

              // 去登录
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(
                  lang.t('go_login'),
                  style: const TextStyle(color: Colors.grey),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInput(
    TextEditingController ctrl,
    IconData icon,
    String hint, {
    bool isPwd = false,
  }) {
    return TextField(
      controller: ctrl,
      obscureText: isPwd,
      decoration: InputDecoration(
        prefixIcon: Icon(icon, color: const Color(0xFF00A1D6)),
        hintText: hint,
        filled: true,
        fillColor: const Color(0xFFF6F7F9),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(50),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}
