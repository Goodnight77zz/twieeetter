import 'dart:io';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:image_picker/image_picker.dart'; // 🔥 选图插件
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app_lang.dart';
import 'api_config.dart';
import 'api_helpers.dart';

class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key});

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  final Color mainBlue = const Color(0xFF00A1D6);

  final TextEditingController _nickCtrl = TextEditingController();
  final TextEditingController _bioCtrl = TextEditingController();

  String? _remoteAvatar; // 原有的网络头像
  File? _localAvatar; // 新选的本地头像
  bool _isSaving = false;
  int userId = 0;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final prefs = await SharedPreferences.getInstance();
    userId = prefs.getInt('userId') ?? 0;

    try {
      var res = await createSessionDio().get('$baseUrl/users/$userId');
      if (mounted) {
        setState(() {
          _nickCtrl.text = res.data['nickname'] ?? "";
          _bioCtrl.text = res.data['bio'] ?? "";
          _remoteAvatar = buildAvatarUrl(res.data);
        });
      }
    } catch (e) {
      print(e);
    }
  }

  // 📸 选择头像
  Future<void> _pickImage() async {
    final picker = ImagePicker();
    final XFile? image = await picker.pickImage(source: ImageSource.gallery);
    if (image != null) {
      setState(() {
        _localAvatar = File(image.path);
      });
    }
  }

  // 💾 保存修改
  Future<void> _save() async {
    setState(() => _isSaving = true);
    final lang = context.read<LangProvider>();

    try {
      FormData formData = FormData.fromMap({
        "nickname": _nickCtrl.text,
        "bio": _bioCtrl.text,
      });

      // 如果选了新图，才上传 avatar 字段
      if (_localAvatar != null) {
        formData.files.add(
          MapEntry(
            "avatar",
            await MultipartFile.fromFile(
              _localAvatar!.path,
              filename: "avatar.jpg",
            ),
          ),
        );
      }

      await createSessionDio().post('$baseUrl/users/$userId', data: formData);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(lang.t('save_success')),
            backgroundColor: Colors.green,
          ),
        );
        Navigator.pop(context, true); // 返回并通知刷新
      }
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text("Error: $e")));
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    // 头像显示逻辑：有本地图显本地，没本地显网络，都没显默认
    ImageProvider avatarImage;
    if (_localAvatar != null) {
      avatarImage = FileImage(_localAvatar!);
    } else if (_remoteAvatar != null) {
      avatarImage = NetworkImage(_remoteAvatar!);
    } else {
      avatarImage = const NetworkImage(
        "https://i0.hdslb.com/bfs/face/member/noface.jpg",
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(lang.t('edit_profile_title'))),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            // 头像区
            Center(
              child: GestureDetector(
                onTap: _pickImage,
                child: Stack(
                  children: [
                    CircleAvatar(radius: 50, backgroundImage: avatarImage),
                    Positioned(
                      bottom: 0,
                      right: 0,
                      child: Container(
                        padding: const EdgeInsets.all(6),
                        decoration: const BoxDecoration(
                          color: Colors.blue,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.camera_alt,
                          size: 18,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 10),
            Text(
              lang.t('change_avatar'),
              style: const TextStyle(color: Colors.grey, fontSize: 12),
            ),

            const SizedBox(height: 30),

            // 输入框
            TextField(
              controller: _nickCtrl,
              decoration: InputDecoration(
                labelText: lang.t('edit_nickname'),
                border: const OutlineInputBorder(),
                prefixIcon: const Icon(Icons.person),
              ),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: _bioCtrl,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: lang.t('edit_bio'),
                border: const OutlineInputBorder(),
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 40),

            // 保存按钮
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed: _isSaving ? null : _save,
                style: ElevatedButton.styleFrom(
                  backgroundColor: mainBlue,
                  foregroundColor: Colors.white,
                ),
                child: _isSaving
                    ? const CircularProgressIndicator(color: Colors.white)
                    : Text(
                        lang.t('save_changes'),
                        style: const TextStyle(fontSize: 16),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
