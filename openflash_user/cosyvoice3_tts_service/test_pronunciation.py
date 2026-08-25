import unittest

try:
    from pronunciation import prepare_pronunciation
except ModuleNotFoundError:
    prepare_pronunciation = None


class PronunciationTest(unittest.TestCase):

    def require_resolver(self):
        self.assertIsNotNone(prepare_pronunciation, "CMU pronunciation resolver is missing")
        return prepare_pronunciation

    def test_converts_unambiguous_word_to_cosyvoice_cmu_tokens(self) -> None:
        pronunciations = {
            "catastrophe": [["K", "AH0", "T", "AE1", "S", "T", "R", "AH0", "F", "IY0"]],
        }

        text, use_text_frontend = self.require_resolver()("catastrophe", pronunciations)

        self.assertEqual("[K][AH0][T][AE1][S][T][R][AH0][F][IY0]", text)
        self.assertFalse(use_text_frontend)

    def test_preserves_trailing_punctuation_after_phonemes(self) -> None:
        pronunciations = {
            "catastrophe": [["K", "AH0", "T", "AE1", "S", "T", "R", "AH0", "F", "IY0"]],
        }

        text, use_text_frontend = self.require_resolver()("catastrophe!", pronunciations)

        self.assertEqual("[K][AH0][T][AE1][S][T][R][AH0][F][IY0]!", text)
        self.assertFalse(use_text_frontend)

    def test_uses_default_cmu_pronunciation_when_dictionary_has_variants(self) -> None:
        pronunciations = {
            "record": [["R", "AH0", "K", "AO1", "R", "D"], ["R", "EH1", "K", "ER0", "D"]],
        }

        text, use_text_frontend = self.require_resolver()("record", pronunciations)

        self.assertEqual("[R][AH0][K][AO1][R][D]", text)
        self.assertFalse(use_text_frontend)

    def test_leaves_sentences_and_unknown_words_unchanged(self) -> None:
        pronunciations = {
            "catastrophe": [["K", "AH0", "T", "AE1", "S", "T", "R", "AH0", "F", "IY0"]],
        }
        resolver = self.require_resolver()

        self.assertEqual(("a catastrophe", True), resolver("a catastrophe", pronunciations))
        self.assertEqual(("codex", True), resolver("codex", pronunciations))


if __name__ == "__main__":
    unittest.main()
