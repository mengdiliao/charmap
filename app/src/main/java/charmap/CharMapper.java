package charmap;
final class CharMapper {

    static int map(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return 26 + (c - 'a');
        if (c >= '0' && c <= '9') return 52 + (c - '0');
        if (c == '_') return 62;
        if (c == '-') return 63;
        return -1;
    }
}