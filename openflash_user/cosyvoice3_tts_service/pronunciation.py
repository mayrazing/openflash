from functools import lru_cache
import re
from typing import Mapping, Sequence

import cmudict


PronunciationDictionary = Mapping[str, Sequence[Sequence[str]]]
_STANDALONE_WORD = re.compile(
    r"(?P<word>[A-Za-z]+(?:['-][A-Za-z]+)*)(?P<punctuation>[.!?,;:]*)"
)


@lru_cache(maxsize=1)
def _load_pronunciations() -> PronunciationDictionary:
    """加载一次离线 CMU 英语发音词典."""
    return cmudict.dict()


def prepare_pronunciation(
    text: str,
    pronunciations: PronunciationDictionary | None = None,
) -> tuple[str, bool]:
    """把词典中的独立英文单词转换成默认美式 CMU 音素."""
    match = _STANDALONE_WORD.fullmatch(text)
    if match is None:
        return text, True

    dictionary = pronunciations if pronunciations is not None else _load_pronunciations()
    candidates = dictionary.get(match.group("word").lower())
    if not candidates:
        return text, True

    phonemes = "".join(f"[{phoneme}]" for phoneme in candidates[0])
    return phonemes + match.group("punctuation"), False
