import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
		
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		String code;
		while(true) {
			int bits = in.readBits(BITS_PER_INT);
			if(bits ==PSEUDO_EOF) {
				break;
			}
			else {
				code = codings[bits];
				out.writeBits(code.length(), Integer.parseInt(code, 2));	
			}
		}
		code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(!(root.myLeft ==null && root.myRight == null)) {
			out.writeBits(1, 0); 
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);;//9 bits of the value stored in leaf
		}
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	private void codingHelper(HuffNode t, String path, String[] encodings) {
		if(t == null)
			return;
		if(t.myLeft == null && t.myRight == null) {//is leaf
			encodings[t.myValue] = path;
			return;
		}
		path += "0" ;
		codingHelper(t.myLeft, path, encodings);
		path = path.substring(0, path.length()-1);
		path += "1";
		codingHelper(t.myRight, path, encodings);
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0; i< counts.length; i++) {
			if(counts[i] > 0)
				pq.add(new HuffNode(i, counts[i], null, null));
		}
		
		while(pq.size()> 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		return pq.remove();//root
	}

	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE +1];
		freq[PSEUDO_EOF] = 1;
		while (true) {
			int read = in.readBits(BITS_PER_WORD);
			if (read == -1) 
				break;
			freq[read]++;
		}
		return freq;
	}

	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE)
			throw new HuffException("Illegal header starts with "+ bits);
		
		HuffNode root  = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		//read the bits from the BitInputStream representing the compressed file one bit at a time
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits ==-1) {
				throw new HuffException("bad input, no PSUEDO_EOF");
			}
			else {
				if(bits ==0) current = current.myLeft;
				else current = current.myRight;
				
				if(current.myLeft == null && current.myRight == null) {//current is a leaf node 
					if(current.myValue == PSEUDO_EOF)
						break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);//write bits for current.value 
						current = root; //start back after leaf
					}
				}
					
			}
		}
		
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);//read a single bit 
		if(bit == -1)
			throw new HuffException("Reading bits failed"); 
		if(bit ==0) {
			HuffNode left = readTreeHeader(in);  //recursive call 
			HuffNode right = readTreeHeader(in);   //recursive call
			return new HuffNode(0, 0, left, right);
		}
		else {
			int value =  in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}