import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'app_lang.dart';
import 'home_tab.dart';
import 'profile_page.dart';
import 'publish_page.dart'; // 🔥 引入发布页

// 将 State 公开，方便 ProfilePage 调用
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => MainScreenState();
}

class MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;

  final List<Widget> _pages = [
    const HomeTab(),
    const PublishPage(), // 🔥 替换原来的 Text 占位
    const ProfilePage(),
  ];

  // 🔥 暴露给外部调用，用于跳转到发布页
  void switchToPublish() {
    setState(() {
      _currentIndex = 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    const mainBlue = Color(0xFF00A1D6);
    final lang = context.watch<LangProvider>();

    return Scaffold(
      body: IndexedStack(
        // 使用 IndexedStack 保持页面状态
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) {
          setState(() => _currentIndex = index);
        },
        selectedItemColor: mainBlue,
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        showUnselectedLabels: true,
        selectedFontSize: 12,
        unselectedFontSize: 12,
        items: [
          BottomNavigationBarItem(
            icon: const Icon(Icons.home),
            label: lang.t('tab_home'),
          ),
          BottomNavigationBarItem(
            icon: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: mainBlue,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.add, color: Colors.white, size: 20),
            ),
            label: lang.t('tab_publish'),
          ),
          BottomNavigationBarItem(
            icon: const Icon(Icons.person),
            label: lang.t('tab_mine'),
          ),
        ],
      ),
    );
  }
}
