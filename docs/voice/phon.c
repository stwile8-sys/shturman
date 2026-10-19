// Фонемизация как в piper-phonemize: по фразам, с типом знака в конце фразы.
// Вывод: строка на фразу — "<punct>\t<sentence_end>\t<ipa>"
#include <stdio.h>
#include <string.h>
#include <espeak-ng/speak_lib.h>
#include "readclause.h"

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: phon <data_path> <text>\n"); return 2; }
    if (espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, argv[1], 0) < 0) { fprintf(stderr, "init failed\n"); return 1; }
    if (espeak_SetVoiceByName("ru") != EE_OK) { fprintf(stderr, "no ru voice\n"); return 1; }
    const char *p = argv[2];
    int terminator = 0;
    while (p != NULL) {
        const char *ph = espeak_TextToPhonemesWithTerminator((const void **)&p, espeakCHARS_AUTO, 0x02, &terminator);
        int punct = terminator & 0x000FFFFF;
        const char *pc = "";
        if (punct == CLAUSE_PERIOD) pc = ".";
        else if (punct == CLAUSE_QUESTION) pc = "?";
        else if (punct == CLAUSE_EXCLAMATION) pc = "!";
        else if (punct == CLAUSE_COMMA) pc = ", ";
        else if (punct == CLAUSE_COLON) pc = ": ";
        else if (punct == CLAUSE_SEMICOLON) pc = "; ";
        int sent = (terminator & CLAUSE_TYPE_SENTENCE) == CLAUSE_TYPE_SENTENCE;
        printf("%s\t%d\t%s\n", pc, sent, ph ? ph : "");
    }
    return 0;
}
