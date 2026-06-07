const String serverUrl = 'http://39.105.193.95:8080';
const String baseUrl = '$serverUrl/api';
const String defaultAvatarUrl =
    'https://i0.hdslb.com/bfs/face/member/noface.jpg';

String? buildUploadUrl(String? fileName) {
  if (fileName == null || fileName.trim().isEmpty) {
    return null;
  }
  final raw = fileName.trim();
  if (raw.startsWith('http://') || raw.startsWith('https://')) {
    return raw;
  }
  final normalized = raw.split('/').last;
  return '$serverUrl/uploads/${Uri.encodeComponent(normalized)}';
}

String buildAvatarUrl(dynamic user) {
  final avatar = user is Map ? user['avatar']?.toString() : null;
  return buildUploadUrl(avatar) ?? defaultAvatarUrl;
}
