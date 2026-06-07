import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'api_helpers.dart';

class PublishPage extends StatefulWidget {
  const PublishPage({super.key});

  @override
  State<PublishPage> createState() => _PublishPageState();
}

class _PublishPageState extends State<PublishPage> {
  final Color mainBlue = const Color(0xFF00A1D6);

  final TextEditingController _titleCtrl = TextEditingController();
  final TextEditingController _contentCtrl = TextEditingController();
  final TextEditingController _authorsCtrl = TextEditingController();
  final TextEditingController _institutionCtrl = TextEditingController();
  final TextEditingController _researchAreaCtrl = TextEditingController();
  final TextEditingController _keywordsCtrl = TextEditingController();
  final TextEditingController _journalCtrl = TextEditingController();
  final TextEditingController _publicationDateCtrl = TextEditingController();
  final TextEditingController _doiCtrl = TextEditingController();
  final TextEditingController _referencesCtrl = TextEditingController();
  final TextEditingController _projectLinksCtrl = TextEditingController();

  PlatformFile? _selectedFile;
  bool _isUploading = false;
  bool _isAiHelping = false;
  String _aiHelperTask = '';
  String _aiHelperMessage = '';

  final List<String> _allTags = [
    'AI',
    'Biomedical',
    'Computer Science',
    'Physics',
    'Chemistry',
    'Deep Learning',
    'Data Analysis',
  ];
  final Set<String> _selectedTags = {};

  String? _contentType = 'paper';
  String? _publicationType = 'journal';
  String? _status = 'draft';
  String? _language = 'English';
  String? _visibility = 'public';

  @override
  void dispose() {
    _titleCtrl.dispose();
    _contentCtrl.dispose();
    _authorsCtrl.dispose();
    _institutionCtrl.dispose();
    _researchAreaCtrl.dispose();
    _keywordsCtrl.dispose();
    _journalCtrl.dispose();
    _publicationDateCtrl.dispose();
    _doiCtrl.dispose();
    _referencesCtrl.dispose();
    _projectLinksCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickFile() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'doc', 'docx'],
    );

    if (result != null && result.files.isNotEmpty) {
      setState(() {
        _selectedFile = result.files.first;
      });
    }
  }

  Future<void> _submit() async {
    final lang = context.read<LangProvider>();
    if (_titleCtrl.text.trim().isEmpty || _contentCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(lang.t('please_input'))));
      return;
    }

    setState(() => _isUploading = true);

    try {
      final prefs = await SharedPreferences.getInstance();
      final userId = prefs.getInt('userId') ?? 0;
      if (userId == 0) {
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(lang.t('login_expired'))));
        return;
      }

      final formData = FormData.fromMap({
        'title': _titleCtrl.text.trim(),
        'content': _contentCtrl.text.trim(),
        'userId': userId,
        'tags': _selectedTags.join(','),
        'authors': _authorsCtrl.text.trim(),
        'institution': _institutionCtrl.text.trim(),
        'researchArea': _researchAreaCtrl.text.trim(),
        'keywords': _keywordsCtrl.text.trim(),
        'contentType': _contentType,
        'publicationType': _publicationType,
        'status': _status,
        'journalOrConference': _journalCtrl.text.trim(),
        'publicationDate': _publicationDateCtrl.text.trim(),
        'doi': _doiCtrl.text.trim(),
        'language': _language,
        'visibility': _visibility,
        'referencesText': _referencesCtrl.text.trim(),
        'projectLinks': _projectLinksCtrl.text.trim(),
      });

      if (_selectedFile?.path != null) {
        formData.files.add(
          MapEntry(
            'file',
            await MultipartFile.fromFile(
              _selectedFile!.path!,
              filename: _selectedFile!.name,
            ),
          ),
        );
      }

      final res = await createSessionDio().post(
        '$baseUrl/tweets',
        data: formData,
      );
      final resultText = res.data.toString().toLowerCase();
      final isSuccess =
          (res.statusCode ?? 500) >= 200 &&
          (res.statusCode ?? 500) < 300 &&
          !resultText.contains('fail') &&
          !resultText.contains('error');

      if (!mounted) {
        return;
      }

      if (isSuccess) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(lang.t('publish_success')),
            backgroundColor: Colors.green,
          ),
        );
        _resetForm();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${lang.t('publish_error')}: ${res.data}')),
        );
      }
    } catch (e) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error: $e')));
    } finally {
      if (mounted) {
        setState(() => _isUploading = false);
      }
    }
  }

  Future<void> _runPublishAiHelper(String task) async {
    final lang = context.read<LangProvider>();
    final title = _titleCtrl.text.trim();
    final content = _contentCtrl.text.trim();

    if (title.isEmpty || content.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Please fill in the title and abstract before using AI assistance.',
          ),
        ),
      );
      return;
    }

    setState(() {
      _isAiHelping = true;
      _aiHelperTask = task;
      _aiHelperMessage = task == 'keywords'
          ? 'AI is extracting keywords...'
          : 'AI is polishing the abstract...';
    });

    try {
      final res = await createSessionDio().post(
        '$baseUrl/ai/publish-helper',
        data: FormData.fromMap({
          'title': title,
          'content': content,
          'task': task,
          'lang': lang.currentLang == 'zh' ? 'zh' : 'en',
        }),
      );

      final result = (res.data['result'] ?? '').toString().trim();
      if (result.isEmpty) {
        throw Exception('Empty AI result');
      }

      if (!mounted) {
        return;
      }

      setState(() {
        if (task == 'keywords') {
          _keywordsCtrl.text = result;
          _aiHelperMessage =
              'Keywords extracted. You can edit them before publishing.';
        } else {
          _contentCtrl.text = result;
          _aiHelperMessage =
              'The abstract has been polished. You can continue refining it.';
        }
      });
    } catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        _aiHelperMessage = 'AI assistance failed. Please try again later.';
      });
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('AI Error: $e')));
    } finally {
      if (mounted) {
        setState(() {
          _isAiHelping = false;
          _aiHelperTask = '';
        });
      }
    }
  }

  void _resetForm() {
    setState(() {
      _titleCtrl.clear();
      _contentCtrl.clear();
      _authorsCtrl.clear();
      _institutionCtrl.clear();
      _researchAreaCtrl.clear();
      _keywordsCtrl.clear();
      _journalCtrl.clear();
      _publicationDateCtrl.clear();
      _doiCtrl.clear();
      _referencesCtrl.clear();
      _projectLinksCtrl.clear();
      _selectedFile = null;
      _selectedTags.clear();
      _contentType = 'paper';
      _publicationType = 'journal';
      _status = 'draft';
      _language = 'English';
      _visibility = 'public';
    });
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: Text(
          lang.t('publish_title'),
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        elevation: 0,
        backgroundColor: Colors.white,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildInput(_titleCtrl, 'Title'),
            const SizedBox(height: 16),
            _buildInput(_contentCtrl, lang.t('content_hint'), maxLines: 7),
            const SizedBox(height: 16),
            _buildAiHelperCard(),
            const SizedBox(height: 20),
            _buildInput(_authorsCtrl, 'Authors / Team'),
            const SizedBox(height: 16),
            _buildInput(_institutionCtrl, 'Institution'),
            const SizedBox(height: 16),
            _buildInput(_researchAreaCtrl, 'Research Area'),
            const SizedBox(height: 16),
            _buildInput(_keywordsCtrl, 'Keywords'),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildDropdown(
                    label: 'Content Type',
                    value: _contentType,
                    items: const {
                      'paper': 'Paper',
                      'dataset': 'Dataset',
                      'project': 'Project',
                      'report': 'Report',
                      'code': 'Code',
                    },
                    onChanged: (value) => setState(() => _contentType = value),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildDropdown(
                    label: 'Publication Type',
                    value: _publicationType,
                    items: const {
                      'journal': 'Journal',
                      'conference': 'Conference',
                      'preprint': 'Preprint',
                      'thesis': 'Thesis',
                      'internal': 'Internal',
                    },
                    onChanged: (value) =>
                        setState(() => _publicationType = value),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildDropdown(
                    label: 'Status',
                    value: _status,
                    items: const {
                      'draft': 'Draft',
                      'ongoing': 'Ongoing',
                      'submitted': 'Submitted',
                      'published': 'Published',
                    },
                    onChanged: (value) => setState(() => _status = value),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildDropdown(
                    label: 'Language',
                    value: _language,
                    items: const {
                      'English': 'English',
                      'Chinese': 'Chinese',
                      'Bilingual': 'Bilingual',
                    },
                    onChanged: (value) => setState(() => _language = value),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _buildDropdown(
              label: 'Visibility',
              value: _visibility,
              items: const {'public': 'Public', 'private': 'Private'},
              onChanged: (value) => setState(() => _visibility = value),
            ),
            const SizedBox(height: 16),
            _buildInput(_journalCtrl, 'Journal / Conference'),
            const SizedBox(height: 16),
            _buildInput(_publicationDateCtrl, 'Publication Date (YYYY-MM-DD)'),
            const SizedBox(height: 16),
            _buildInput(_doiCtrl, 'DOI'),
            const SizedBox(height: 16),
            _buildInput(_referencesCtrl, 'References', maxLines: 4),
            const SizedBox(height: 16),
            _buildInput(_projectLinksCtrl, 'Project Links', maxLines: 3),
            const SizedBox(height: 20),
            GestureDetector(
              onTap: _pickFile,
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 24),
                decoration: BoxDecoration(
                  color: _selectedFile == null
                      ? const Color(0xFFF0F2F5)
                      : const Color(0xFFE3F2FD),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: _selectedFile == null
                        ? Colors.grey.shade300
                        : mainBlue,
                    width: 1.5,
                  ),
                ),
                child: Column(
                  children: [
                    Icon(
                      _selectedFile == null
                          ? Icons.cloud_upload_outlined
                          : Icons.check_circle,
                      size: 40,
                      color: _selectedFile == null ? Colors.grey : mainBlue,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      _selectedFile == null
                          ? lang.t('upload_area')
                          : '${lang.t('file_selected')}${_selectedFile!.name}',
                      style: TextStyle(
                        color: _selectedFile == null ? Colors.grey : mainBlue,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    if (_selectedFile == null)
                      Text(
                        lang.t('upload_sub_hint'),
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Text(
              lang.t('tags_label'),
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: _allTags.map((tag) {
                final isSelected = _selectedTags.contains(tag);
                return ChoiceChip(
                  label: Text(lang.tTag(tag)),
                  selected: isSelected,
                  selectedColor: mainBlue,
                  labelStyle: TextStyle(
                    color: isSelected ? Colors.white : Colors.black87,
                  ),
                  backgroundColor: const Color(0xFFF0F2F5),
                  onSelected: (selected) {
                    setState(() {
                      if (selected) {
                        _selectedTags.add(tag);
                      } else {
                        _selectedTags.remove(tag);
                      }
                    });
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 36),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed: _isUploading ? null : _submit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: mainBlue,
                  foregroundColor: Colors.white,
                  elevation: 2,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(25),
                  ),
                ),
                child: _isUploading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )
                    : Text(
                        lang.t('publish_now'),
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAiHelperCard() {
    final keywordsBusy = _isAiHelping && _aiHelperTask == 'keywords';
    final polishBusy = _isAiHelping && _aiHelperTask == 'polish';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFF5F9FF), Color(0xFFEDF7FF)],
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFD6E9F8)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.auto_awesome, size: 18, color: Color(0xFF2563EB)),
              SizedBox(width: 8),
              Text(
                'AI Publish Assistant',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF0F172A),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Use the current title and abstract to generate keywords or polish the abstract before publishing.',
            style: TextStyle(
              fontSize: 12,
              height: 1.5,
              color: Color(0xFF475569),
            ),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _buildAiHelperButton(
                icon: Icons.key_outlined,
                label: keywordsBusy ? 'Extracting...' : 'AI Keywords',
                enabled: !_isAiHelping,
                onTap: () => _runPublishAiHelper('keywords'),
              ),
              _buildAiHelperButton(
                icon: Icons.brush_outlined,
                label: polishBusy ? 'Polishing...' : 'Polish Abstract',
                enabled: !_isAiHelping,
                onTap: () => _runPublishAiHelper('polish'),
              ),
            ],
          ),
          if (_aiHelperMessage.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.85),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFD9E8F5)),
              ),
              child: Text(
                _aiHelperMessage,
                style: const TextStyle(
                  fontSize: 12,
                  height: 1.5,
                  color: Color(0xFF334155),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAiHelperButton({
    required IconData icon,
    required String label,
    required bool enabled,
    required VoidCallback onTap,
  }) {
    return ElevatedButton.icon(
      onPressed: enabled ? onTap : null,
      style: ElevatedButton.styleFrom(
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF1D4ED8),
        elevation: 0,
        side: const BorderSide(color: Color(0xFFBFDBFE)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      ),
      icon: Icon(icon, size: 18),
      label: Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
    );
  }

  Widget _buildInput(
    TextEditingController controller,
    String label, {
    int maxLines = 1,
  }) {
    return TextField(
      controller: controller,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: label,
        filled: true,
        fillColor: const Color(0xFFF6F7F9),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }

  Widget _buildDropdown({
    required String label,
    required String? value,
    required Map<String, String> items,
    required ValueChanged<String?> onChanged,
  }) {
    return DropdownButtonFormField<String>(
      value: value,
      items: items.entries
          .map(
            (entry) => DropdownMenuItem<String>(
              value: entry.key,
              child: Text(entry.value),
            ),
          )
          .toList(),
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: label,
        filled: true,
        fillColor: const Color(0xFFF6F7F9),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}
