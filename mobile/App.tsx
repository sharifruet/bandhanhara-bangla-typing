import { useCallback, useState } from 'react';
import { Text, TouchableOpacity, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import * as Clipboard from 'expo-clipboard';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { BanglaKeyboard } from './components/BanglaKeyboard';

SplashScreen.preventAutoHideAsync();

export default function App() {
  const [text, setText] = useState('');

  const [fontsLoaded] = useFonts({
    NotoSansBengali: require('./assets/fonts/NotoSansBengali-Regular.ttf'),
  });

  const onLayoutRootView = useCallback(async () => {
    if (fontsLoaded) await SplashScreen.hideAsync();
  }, [fontsLoaded]);

  if (!fontsLoaded) return null;

  const handleChar = (char: string) => setText(t => t + char);
  const handleBackspace = () => setText(t => [...t].slice(0, -1).join(''));
  const handleEnter = () => setText(t => t + '\n');
  const handleCopy = () => Clipboard.setStringAsync(text);

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container} edges={['top', 'left', 'right']} onLayout={onLayoutRootView}>
        <StatusBar style="dark" />

        {/* Output area — no scroll, text fills space */}
        <View style={styles.outputArea}>
          <Text style={[styles.outputText, !text && styles.placeholder]} numberOfLines={6}>
            {text || 'এখানে টাইপ করুন…'}
          </Text>
          <TouchableOpacity
            style={[styles.copyBtn, !text && styles.copyBtnDisabled]}
            onPress={handleCopy}
            disabled={!text}
          >
            <Text style={styles.copyBtnText}>Copy</Text>
          </TouchableOpacity>
        </View>

        <BanglaKeyboard
          onChar={handleChar}
          onBackspace={handleBackspace}
          onEnter={handleEnter}
        />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  outputArea: {
    flex: 1,
    padding: 16,
    justifyContent: 'space-between',
  },
  outputText: {
    fontSize: 24,
    lineHeight: 40,
    color: '#111827',
    fontFamily: 'NotoSansBengali',
    flexShrink: 1,
  },
  placeholder: {
    color: '#9ca3af',
  },
  copyBtn: {
    alignSelf: 'flex-end',
    paddingVertical: 8,
    paddingHorizontal: 20,
    backgroundColor: '#2563eb',
    borderRadius: 6,
  },
  copyBtnDisabled: {
    opacity: 0.4,
  },
  copyBtnText: {
    color: '#ffffff',
    fontSize: 14,
  },
});
