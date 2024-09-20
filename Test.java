import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;


public class Test {

	public static void main(String[] args) {

		ArrayList<String> chars = Arrays.asList("hello world".split("")).stream().collect(Collectors.toCollection(ArrayList::new));
		HashMap<String, Integer> charCounts = new HashMap<String, Integer>();
		for(String ch : chars) {
			Integer count = charCounts.putIfAbsent(ch, 1);
			if(count != null)
				charCounts.put(ch, count + 1);
			
		}
		System.out.println(charCounts);

	}

}
